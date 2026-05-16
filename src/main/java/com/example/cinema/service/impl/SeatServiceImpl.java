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

        Set<Long> occupied = tickets.stream()
                .filter(t -> !"CANCELLED".equalsIgnoreCase(t.getStatus()))
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

    // ================= CHỐNG ĐỂ GHẾ TRỐNG ĐƠN LẺ (TEST CASE ĐẶT VÉ) =================
    @Override
    public void validateSeatSelection(Long showtimeId, List<Long> selectedSeatIds) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu"));

        List<Seat> allSeats = seatRepository.findByRoomId(showtime.getRoom().getId());
        List<Ticket> tickets = ticketRepository.findByShowtimeId(showtimeId);

        Set<Long> alreadyOccupied = tickets.stream()
                .filter(t -> !"CANCELLED".equalsIgnoreCase(t.getStatus()))
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        Set<Long> newlySelected = new HashSet<>(selectedSeatIds);

        Map<String, List<Seat>> seatsByRow = allSeats.stream()
                .collect(Collectors.groupingBy(Seat::getSeatRow));

        for (Map.Entry<String, List<Seat>> entry : seatsByRow.entrySet()) {
            List<Seat> rowSeats = entry.getValue();
            rowSeats.sort(Comparator.comparingInt(s -> Integer.parseInt(s.getSeatNumber())));

            int n = rowSeats.size();
            for (int i = 0; i < n; i++) {
                Seat currentSeat = rowSeats.get(i);
                Long currentId = currentSeat.getId();

                boolean isFreePostBooking = !alreadyOccupied.contains(currentId) && !newlySelected.contains(currentId);

                if (isFreePostBooking) {
                    boolean leftIsBlocked = (i == 0) 
                            || alreadyOccupied.contains(rowSeats.get(i - 1).getId()) 
                            || newlySelected.contains(rowSeats.get(i - 1).getId());
                            
                    boolean rightIsBlocked = (i == n - 1) 
                            || alreadyOccupied.contains(rowSeats.get(i + 1).getId()) 
                            || newlySelected.contains(rowSeats.get(i + 1).getId());

                    if (leftIsBlocked && rightIsBlocked) {
                        boolean causedByUser = false;
                        if (i > 0 && newlySelected.contains(rowSeats.get(i - 1).getId())) causedByUser = true;
                        if (i < n - 1 && newlySelected.contains(rowSeats.get(i + 1).getId())) causedByUser = true;

                        if (causedByUser) {
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

        if (ticketRepository.existsBySeatId(id)) {
            throw new RuntimeException("Ghế đã có lịch sử đặt vé, không thể xóa!");
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
        seatRepository.deleteByRoomId(roomId);
    }

    // ================= NEW METHOD: CHECK ELIGIBILITY FOR FRONTEND =================
    @Override
    public Map<String, Boolean> checkSeatEligibility(Long id) {
        // Kiểm tra xem ghế này đã được mua vé nào chưa
        boolean hasTickets = ticketRepository.existsBySeatId(id);
        
        Map<String, Boolean> response = new HashMap<>();
        // Nếu chưa có vé (hasTickets = false) -> canDelete = true (được phép xóa)
        response.put("canDelete", !hasTickets); 
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