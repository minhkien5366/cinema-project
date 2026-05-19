package com.example.cinema.service.impl;

import com.example.cinema.dto.SeatRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.SeatService;
import com.example.cinema.repository.SeatPriceConfigRepository;
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
    
    // TIÊM THÊM REPOSITORY CẤU HÌNH GIÁ ĐỂ TÍNH GIÁ ĐỘNG CHO FE
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

        // 🔥 ĐẢM BẢO AN TOÀN: Lọc loại trừ cuống vé null nếu có
        Set<Long> occupied = tickets.stream()
                .filter(t -> !"CANCELLED".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getSeat() != null) 
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        // ĐOẠN ĐỔI GIÁ ĐỘNG: Tính toán thứ trong tuần của suất chiếu (2 = Thứ 2, ..., 8 = Chủ Nhật)
        int javaDay = showtime.getStartTime().getDayOfWeek().getValue();
        int dayValue = (javaDay == 7) ? 8 : javaDay + 1;

        for (Seat s : seats) {
            // 1. Cập nhật trạng thái trống/đã đặt
            s.setStatus(occupied.contains(s.getId()) ? "OCCUPIED" : "AVAILABLE");
            
            // 2. CẬP NHẬT GIÁ ĐỘNG TẠI ĐÂY ĐỂ TRẢ VỀ CHO FE ĐÚNG GIÁ CHỦ NHẬT
            Double dynamicPrice = seatPriceConfigRepository
                .findBySeatTypeAndDayOfWeek(
                    s.getSeatType().toUpperCase(),
                    dayValue
                )
                .map(SeatPriceConfig::getPrice)
                .orElse(s.getPrice()); // Nếu không có cấu hình ngày đó thì lấy giá mặc định của ghế
                
            s.setPrice(dynamicPrice);
        }

        return seats;
    }

    // ================= CHỐNG ĐỂ GHẾ TRỐNG ĐƠN LẺ CHUẨN CGV (THUẬT TOÁN TOẠ ĐỘ TUYỆT ĐỐI) =================
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

        // Gom nhóm ghế theo từng hàng (A, B, C...) để quét độc lập
        Map<String, List<Seat>> seatsByRow = allSeats.stream()
                .collect(Collectors.groupingBy(Seat::getSeatRow));

        for (Map.Entry<String, List<Seat>> entry : seatsByRow.entrySet()) {
            List<Seat> rowSeats = entry.getValue();
            
            // Ánh xạ nhanh: Số ghế tuyệt đối -> Đối tượng ghế vật lý (Giải quyết triệt để lỗi khuyết số ghế/lối đi)
            Map<Integer, Seat> seatMapByNum = new HashMap<>();
            for (Seat s : rowSeats) {
                try {
                    seatMapByNum.put(Integer.parseInt(s.getSeatNumber()), s);
                } catch (NumberFormatException e) {
                    // Phòng hờ trường hợp số ghế chứa ký tự lạ
                }
            }

            for (Seat currentSeat : rowSeats) {
                Long currentId = currentSeat.getId();
                boolean isOccupied = alreadyOccupied.contains(currentId);
                boolean isSelected = newlySelected.contains(currentId);

                // CHỈ QUÉT: Những ghế thực sự còn TRỐNG sau khi giả định đơn hàng này đặt thành công
                if (!isOccupied && !isSelected) {
                    int currentNum = Integer.parseInt(currentSeat.getSeatNumber());

                    // --- KIỂM TRA BIÊN TRÁI (Số ghế tuyệt đối - 1) ---
                    Seat leftSeat = seatMapByNum.get(currentNum - 1);
                    boolean leftBlocked = false;
                    boolean leftSelected = false;
                    if (leftSeat == null) {
                        leftBlocked = true; // Không có ghế lân cận -> Tường rạp hoặc Lối đi (Hợp lệ)
                    } else {
                        boolean leftOccupied = alreadyOccupied.contains(leftSeat.getId());
                        boolean leftSimSelected = newlySelected.contains(leftSeat.getId());
                        if (leftOccupied || leftSimSelected) {
                            leftBlocked = true;
                            if (leftSimSelected) leftSelected = true;
                        }
                    }

                    // --- KIỂM TRA BIÊN PHẢI (Số ghế tuyệt đối + 1) ---
                    Seat rightSeat = seatMapByNum.get(currentNum + 1);
                    boolean rightBlocked = false;
                    boolean rightSelected = false;
                    if (rightSeat == null) {
                        rightBlocked = true; // Không có ghế lân cận -> Tường rạp hoặc Lối đi (Hợp lệ)
                    } else {
                        boolean rightOccupied = alreadyOccupied.contains(rightSeat.getId());
                        boolean rightSimSelected = newlySelected.contains(rightSeat.getId());
                        if (rightOccupied || rightSimSelected) {
                            rightBlocked = true;
                            if (rightSimSelected) rightSelected = true;
                        }
                    }

                    // NẾU HAI BÊN GHẾ TRỐNG ĐỀU BỊ CHẶN CỨNG -> ĐÂY LÀ GHẾ ĐƠN LẺ BỊ CÔ LẬP
                    if (leftBlocked && rightBlocked) {
                        // Chỉ cấu thành lỗi nếu khe trống cô lập này sinh ra trực tiếp do lượt chọn của chính user
                        if (leftSelected || rightSelected) {
                            throw new RuntimeException("Không được để lại ghế trống đơn lẻ (" + currentSeat.getName() + ") ở giữa hoặc đầu hàng!");
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

        // 🎯 RÀNG BUỘC KINH DOANH CHẶN TẬNG CỨNG: Nếu phòng đã được lên lịch chiếu -> Khóa tính năng tạo sơ đồ
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

        // 🎯 RÀNG BUỘC KINH DOANH CHẶN TẬNG CỨNG: Có suất chiếu hoạt động -> Cấm chèn lẻ thêm ghế mới
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

        // 🎯 RÀNG BUỘC KINH DOANH CHẶN TẬNG CỨNG: Có suất chiếu hoạt động -> Cấm cập nhật thông tin/đổi loại ghế mẫu
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

    // ================= RÀNG BUỘC 3: KIỂM TRA ĐỊNH DẠNG CẶP GHẾ ĐÔI (SWEETBOX) =================
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

        // 🎯 RÀNG BUỘC KINH DOANH CHẶN TẬNG CỨNG: Có suất chiếu hoạt động -> Chặn đứng hành vi xóa ghế lẻ mẫu
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

        // 🎯 RÀNG BUỘC KINH DOANH CHẶN TẬNG CỨNG: Có suất chiếu hoạt động -> Khóa tính năng dọn sạch phòng mẫu
        if (showtimeRepository.existsByRoom_Id(roomId)) {
            throw new RuntimeException("Phòng đã có suất chiếu, không thể dọn sạch sơ đồ ghế!");
        }

        seatRepository.deleteByRoomId(roomId);
    }

    // ================= NEW METHOD: CHECK ELIGIBILITY FOR FRONTEND =================
    @Override
    public Map<String, Boolean> checkSeatEligibility(Long id) {
        Seat seat = seatRepository.findById(id).orElse(null);
        boolean canDelete = true;

        if (seat != null && seat.getRoom() != null) {
            // 🎯 NẾU PHÒNG ĐÃ LÊN SUẤT CHIẾU -> Trả về false ngay để Next.js bật Lock Modal "Không thể xóa" lên
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