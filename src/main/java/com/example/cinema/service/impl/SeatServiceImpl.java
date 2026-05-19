package com.example.cinema.service.impl;

import com.example.cinema.dto.SeatRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final ShowtimeRepository showtimeRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SeatPriceConfigRepository seatPriceConfigRepository;

    // ================= PRICE CONFIG =================
    private static final double PRICE_NORMAL = 80000.0;
    private static final double PRICE_VIP = 120000.0;
    private static final double PRICE_SWEETBOX = 250000.0;

    // ================= SHOWTIME & AVAILABILITY =================
    @Override
    public List<Seat> getSeatsByShowtime(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu"));

        if (showtime.getRoom() == null) return new ArrayList<>();

        List<Seat> seats = seatRepository.findByRoomId(showtime.getRoom().getId());
        List<Ticket> tickets = ticketRepository.findByShowtimeId(showtimeId);

        Set<Long> occupied = tickets.stream()
                .filter(t -> !"CANCELLED".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getSeat() != null) 
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        int javaDay = showtime.getStartTime().getDayOfWeek().getValue();
        int dayValue = (javaDay == 7) ? 8 : javaDay + 1;

        for (Seat s : seats) {
            s.setStatus(occupied.contains(s.getId()) ? "OCCUPIED" : "AVAILABLE");
            
            Double dynamicPrice = seatPriceConfigRepository
                .findBySeatTypeAndDayOfWeek(s.getSeatType().toUpperCase(), dayValue)
                .map(SeatPriceConfig::getPrice)
                .orElse(s.getPrice());
                
            s.setPrice(dynamicPrice);
        }

        return seats;
    }

    // ================= THUẬT TOÁN KIỂM TRONG GHẾ TRỐNG ĐƠN LẺ NÂNG CẤP CHUẨN CGV 100% =================
    @Override
    public void validateSeatSelection(Long showtimeId, List<Long> selectedSeatIds) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu"));

        List<Seat> allSeats = seatRepository.findByRoomId(showtime.getRoom().getId());
        List<Ticket> tickets = ticketRepository.findByShowtimeId(showtimeId);

        Set<Long> alreadyOccupied = tickets.stream()
                .filter(t -> !"CANCELLED".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getSeat() != null) 
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        Set<Long> newlySelected = new HashSet<>(selectedSeatIds);

        // Gom nhóm toàn bộ ghế theo từng Hàng (A, B, C...) để quét độc lập từng hàng một
        Map<String, List<Seat>> seatsByRow = allSeats.stream()
                .collect(Collectors.groupingBy(Seat::getSeatRow));

        for (Map.Entry<String, List<Seat>> entry : seatsByRow.entrySet()) {
            List<Seat> rowSeats = entry.getValue();
            
            // Xây dựng bản đồ tọa độ Số ghế thực tế trong hàng
            Map<Integer, Seat> seatMapByNum = new HashMap<>();
            for (Seat s : rowSeats) {
                try {
                    seatMapByNum.put(Integer.parseInt(s.getSeatNumber()), s);
                } catch (NumberFormatException e) {}
            }

            for (Seat currentSeat : rowSeats) {
                // 🎯 ĐIỀU KIỆN LOẠI TRỪ 1: Nếu là ghế đôi (SWEETBOX / COUPLE) thì THẢ XÍCH, bỏ qua không quét lỗi kẹp ghế lẻ!
                String seatType = currentSeat.getSeatType() != null ? currentSeat.getSeatType().toUpperCase() : "NORMAL";
                if ("SWEETBOX".equals(seatType) || "COUPLE".equals(seatType)) {
                    continue; 
                }

                Long currentId = currentSeat.getId();
                boolean isOccupied = alreadyOccupied.contains(currentId);
                boolean isSelected = newlySelected.contains(currentId);

                // CHỈ QUÉT: Những ghế còn TRỐNG sau khi user giả định bấm đặt hàng thành công
                if (!isOccupied && !isSelected) {
                    int currentNum = Integer.parseInt(currentSeat.getSeatNumber());

                    // --- KIỂM TRA BIÊN KẸP BÊN TRÁI (Số ghế - 1) ---
                    Seat leftSeat = seatMapByNum.get(currentNum - 1);
                    boolean leftBlocked = false;
                    boolean leftSelected = false;
                    
                    if (leftSeat == null) {
                        // Trái không có ghế -> Là TƯỜNG rạp hoặc LỐI ĐI. Biên này mở tự do, không tính là bị chặn cứng!
                        leftBlocked = false; 
                    } else {
                        boolean leftOccupied = alreadyOccupied.contains(leftSeat.getId());
                        boolean leftSimSelected = newlySelected.contains(leftSeat.getId());
                        if (leftOccupied || leftSimSelected) {
                            leftBlocked = true; // Bị chặn bởi ghế đã mua hoặc ghế đang chọn
                            if (leftSimSelected) leftSelected = true;
                        }
                    }

                    // --- KIỂM TRA BIÊN KẸP BÊN PHẢI (Số ghế + 1) ---
                    Seat rightSeat = seatMapByNum.get(currentNum + 1);
                    boolean rightBlocked = false;
                    boolean rightSelected = false;
                    
                    if (rightSeat == null) {
                        // Phải không có ghế -> Là TƯỜNG rạp hoặc LỐI ĐI. Biên này mở tự do, không tính là bị chặn cứng!
                        rightBlocked = false; 
                    } else {
                        boolean rightOccupied = alreadyOccupied.contains(rightSeat.getId());
                        boolean rightSimSelected = newlySelected.contains(rightSeat.getId());
                        if (rightOccupied || rightSimSelected) {
                            rightBlocked = true; // Bị chặn bởi ghế đã mua hoặc ghế đang chọn
                            if (rightSimSelected) rightSelected = true;
                        }
                    }

                    // 🎯 ĐIỀU KIỆN QUYẾT ĐỊNH CHUẨN CGV: Chỉ cấu thành lỗi nếu khe trống đơn lẻ này bị CHẶN CỨNG 2 ĐẦU GIỮA HÀNG
                    // Nếu một trong 2 bên là Tường/Lối đi (leftBlocked hoặc rightBlocked bằng false) -> HỢP LỆ hoàn toàn, cho qua!
                    if (leftBlocked && rightBlocked) {
                        // Khe trống đơn lẻ nằm kẹt ở giữa hàng, và lỗi này trực tiếp sinh ra do lượt chọn ghế của user hiện tại
                        if (leftSelected || rightSelected) {
                            throw new RuntimeException("Không được để lại ghế trống đơn lẻ (" + currentSeat.getName() + ") ở giữa hàng ghế!");
                        }
                    }
                }
            }
        }
    }

    // ================= AUTO GENERATE =================
    @Override
    @Transactional
    public List<Seat> generateSeatsForRoom(Long roomId, int numRows, int seatsPerRow) {
        validateRoomAccess(roomId);

        if (showtimeRepository.existsByRoom_Id(roomId)) {
            throw new RuntimeException("Phòng đã có suất chiếu, không thể chỉnh sửa hoặc làm lại sơ đồ ghế!");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        int total = numRows * seatsPerRow;
        if (total > room.getTotalSeats()) {
            throw new RuntimeException("Vượt quá sức chứa phòng");
        }

        seatRepository.deleteByRoomId(roomId);

        List<Seat> seats = new ArrayList<>();
        char row = 'A';

        for (int i = 0; i < numRows; i++) {
            for (int j = 1; j <= seatsPerRow; j++) {
                Seat seat = new Seat();
                seat.setRoom(room);
                seat.setSeatRow(String.valueOf(row));
                seat.setSeatNumber(String.valueOf(j));
                seat.setName(row + String.valueOf(j));
                seat.setStatus("AVAILABLE");
                seat.setSeatType("NORMAL");
                seat.setPrice(PRICE_NORMAL);
                seats.add(seat);
            }
            row++;
        }

        return seatRepository.saveAll(seats);
    }

    // ================= CREATE =================
    @Override
    @Transactional
    public Seat createSeat(SeatRequest request) {
        validateRoomAccess(request.getRoomId());

        if (showtimeRepository.existsByRoom_Id(request.getRoomId())) {
            throw new RuntimeException("Phòng đã có suất chiếu, không thể chèn thêm ghế mới!");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        long count = seatRepository.countByRoomId(room.getId());
        if (count >= room.getTotalSeats()) {
            throw new RuntimeException("Phòng đã đầy, không thể thêm ghế mới");
        }

        List<Seat> existingSeats = seatRepository.findByRoomId(room.getId());
        boolean isDuplicate = existingSeats.stream().anyMatch(s -> 
            s.getSeatRow().equalsIgnoreCase(request.getSeatRow()) && 
            s.getSeatNumber().equalsIgnoreCase(String.valueOf(request.getSeatNumber()))
        );
        if (isDuplicate) {
            throw new RuntimeException("Vị trí ghế " + request.getSeatRow() + request.getSeatNumber() + " đã tồn tại trong phòng này!");
        }

        Seat seat = new Seat();
        seat.setRoom(room);
        seat.setStatus("AVAILABLE");

        mapRequestToEntity(request, seat, room);
        validateSweetboxPairing(room.getId(), seat, false);

        return seatRepository.save(seat);
    }

    // ================= UPDATE =================
    @Override
    @Transactional
    public Seat updateSeat(Long id, SeatRequest request) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ghế"));

        validateRoomAccess(seat.getRoom().getId());

        if (showtimeRepository.existsByRoom_Id(seat.getRoom().getId())) {
            throw new RuntimeException("Phòng đã có suất chiếu, không thể thay đổi thông tin sơ đồ ghế!");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        List<Seat> existingSeats = seatRepository.findByRoomId(room.getId());
        boolean isDuplicate = existingSeats.stream().anyMatch(s -> 
            !s.getId().equals(id) &&
            s.getSeatRow().equalsIgnoreCase(request.getSeatRow()) && 
            s.getSeatNumber().equalsIgnoreCase(String.valueOf(request.getSeatNumber()))
        );
        if (isDuplicate) {
            throw new RuntimeException("Vị trí ghế cập nhật mới đã bị trùng!");
        }

        mapRequestToEntity(request, seat, room);

        if (request.getStatus() != null) {
            seat.setStatus(request.getStatus());
        }

        validateSweetboxPairing(room.getId(), seat, true);

        return seatRepository.save(seat);
    }

    private void validateSweetboxPairing(Long roomId, Seat targetSeat, boolean isUpdate) {
        if (!"SWEETBOX".equalsIgnoreCase(targetSeat.getSeatType())) return;

        int seatNum = Integer.parseInt(targetSeat.getSeatNumber());
        int partnerNumber = (seatNum % 2 != 0) ? (seatNum + 1) : (seatNum - 1);

        List<Seat> existingSeats = seatRepository.findByRoomId(roomId);
        boolean hasPartner = existingSeats.stream().anyMatch(s -> 
            s.getSeatRow().equalsIgnoreCase(targetSeat.getSeatRow()) && 
            s.getSeatNumber().equals(String.valueOf(partnerNumber)) &&
            "SWEETBOX".equalsIgnoreCase(s.getSeatType())
        );

        if (!hasPartner && !isUpdate) {
            System.out.println("[Cảnh báo] Ghế Sweetbox " + targetSeat.getName() + " đang thiếu cặp liền kề (" + targetSeat.getSeatRow() + partnerNumber + ")");
        }
    }

    // ================= DELETE =================
    @Override
    @Transactional
    public void deleteSeat(Long id) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));

        validateRoomAccess(seat.getRoom().getId());

        if (showtimeRepository.existsByRoom_Id(seat.getRoom().getId())) {
            throw new RuntimeException("Phòng đã có suất chiếu, không thể thực hiện xóa ghế!");
        }
        
        seatRepository.deleteById(id);
    }

    // ================= ROOM MANAGEMENT =================
    @Override
    public List<Seat> getSeatsByRoom(Long roomId) {
        validateRoomAccess(roomId);
        return seatRepository.findByRoomId(roomId);
    }

    @Override
    public List<Seat> getAllSeats() {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return seatRepository.findAll();
        return seatRepository.findByRoom_CinemaItem_Id(user.getManagedCinemaItemId());
    }

    @Override
    @Transactional
    public void deleteSeatsByRoom(Long roomId) {
        validateRoomAccess(roomId);

        if (showtimeRepository.existsByRoom_Id(roomId)) {
            throw new RuntimeException("Phòng đã có suất chiếu, không thể dọn sạch sơ đồ ghế!");
        }

        seatRepository.deleteByRoomId(roomId);
    }

    @Override
    public Map<String, Boolean> checkSeatEligibility(Long id) {
        Seat seat = seatRepository.findById(id).orElse(null);
        boolean canDelete = true;

        if (seat != null && seat.getRoom() != null) {
            if (showtimeRepository.existsByRoom_Id(seat.getRoom().getId())) {
                canDelete = false;
            }
        } else {
            canDelete = false;
        }
        
        Map<String, Boolean> response = new HashMap<>();
        response.put("canDelete", canDelete); 
        return response;
    }

    // ================= MAPPER =================
    private void mapRequestToEntity(SeatRequest request, Seat seat, Room room) {
        seat.setSeatRow(request.getSeatRow());
        seat.setSeatNumber(String.valueOf(request.getSeatNumber()));
        seat.setName(request.getSeatRow() + request.getSeatNumber());
        seat.setRoom(room);

        String type = request.getSeatType();
        if (type == null) type = "NORMAL";

        type = type.toUpperCase();
        seat.setSeatType(type);

        switch (type) {
            case "VIP":
                seat.setPrice(PRICE_VIP);
                break;
            case "SWEETBOX":
                seat.setPrice(PRICE_SWEETBOX);
                break;
            default:
                seat.setPrice(PRICE_NORMAL);
        }
    }

    // ================= SECURITY ACCESS CONTROL =================
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập không tồn tại hoặc hết hạn"));
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().toUpperCase().contains("ADMIN"));
    }

    private void validateRoomAccess(Long roomId) {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return;

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        if (room.getCinemaItem() == null ||
                !room.getCinemaItem().getId().equals(user.getManagedCinemaItemId())) {
            throw new RuntimeException("Bạn không có quyền quản lý phòng chiếu của rạp này!");
        }
    }
}