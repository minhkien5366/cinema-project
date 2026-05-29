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

import java.time.LocalDateTime;
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

    // ================= LOGIC KIỂM TRA SUẤT CHIẾU TƯƠNG LAI =================
    /**
     * Kiểm tra xem phòng có suất chiếu nào ĐANG DIỄN RA hoặc SẮP DIỄN RA không.
     * Nếu các suất chiếu đã kết thúc (dù chỉ 1 phút trước) -> trả về false (Cho phép xóa/sửa)
     */
    private boolean hasActiveOrFutureShowtimes(Long roomId) {
        List<Showtime> showtimes = showtimeRepository.findByRoomId(roomId);
        if (showtimes == null || showtimes.isEmpty()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        for (Showtime st : showtimes) {
            if (st.getStartTime() == null) continue;

            // Mặc định thời lượng phim là 150 phút (2.5 tiếng) nếu không lấy được dữ liệu thời lượng
            int durationMinutes = 150; 
            try {
                if (st.getMovie() != null && st.getMovie().getDuration() != null) {
                    durationMinutes = st.getMovie().getDuration();
                }
            } catch (Exception e) {
                // Bỏ qua lỗi, dùng giá trị mặc định 150 phút
            }

            // Thời điểm kết thúc suất chiếu = Thời gian bắt đầu + thời lượng phim
            LocalDateTime endTime = st.getStartTime().plusMinutes(durationMinutes);

            // Nếu thời điểm hiện tại VẪN TRƯỚC thời điểm kết thúc => Suất chiếu đang hoặc sắp diễn ra
            if (now.isBefore(endTime)) {
                return true; 
            }
        }
        return false; // Tất cả suất chiếu đều đã diễn ra xong trong quá khứ
    }

    // ================= SHOWTIME & AVAILABILITY =================
    @Override
    public List<Seat> getSeatsByShowtime(Long showtimeId) {

        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Không tìm thấy suất chiếu"));

        if (showtime.getRoom() == null) {
            return new ArrayList<>();
        }

        List<Seat> seats =
                seatRepository.findByRoomId(showtime.getRoom().getId());

        List<Ticket> tickets =
                ticketRepository.findByShowtimeId(showtimeId);

        Set<Long> occupied = tickets.stream()
                .filter(t ->
                        !"CANCELLED".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getSeat() != null)
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        int javaDay = showtime.getStartTime()
                .getDayOfWeek()
                .getValue();

        int dayValue = (javaDay == 7)
                ? 8
                : javaDay + 1;

        for (Seat s : seats) {

            s.setStatus(
                    occupied.contains(s.getId())
                            ? "OCCUPIED"
                            : "AVAILABLE"
            );

            Double dynamicPrice =
                    seatPriceConfigRepository
                            .findBySeatTypeAndDayOfWeek(
                                    s.getSeatType().toUpperCase(),
                                    dayValue
                            )
                            .map(SeatPriceConfig::getPrice)
                            .orElse(s.getPrice());

            s.setPrice(dynamicPrice);
        }

        return seats;
    }

    // ================= VALIDATE GHẾ =================
    @Override
    public void validateSeatSelection(
            Long showtimeId,
            List<Long> selectedSeatIds
    ) {

        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Không tìm thấy suất chiếu"));

        List<Seat> allSeats =
                seatRepository.findByRoomId(showtime.getRoom().getId());

        List<Ticket> tickets =
                ticketRepository.findByShowtimeId(showtimeId);

        Set<Long> alreadyOccupied = tickets.stream()
                .filter(t ->
                        !"CANCELLED".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getSeat() != null)
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        Set<Long> newlySelected = new HashSet<>(selectedSeatIds);

        Map<String, List<Seat>> seatsByRow = allSeats.stream()
                .collect(Collectors.groupingBy(Seat::getSeatRow));

        for (Map.Entry<String, List<Seat>> entry : seatsByRow.entrySet()) {

            List<Seat> rowSeats = entry.getValue();

            Map<Integer, Seat> seatMapByNum = new HashMap<>();

            for (Seat s : rowSeats) {
                try {
                    seatMapByNum.put(
                            Integer.parseInt(s.getSeatNumber()),
                            s
                    );
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }

            for (Seat currentSeat : rowSeats) {

                String seatType =
                        currentSeat.getSeatType() != null
                                ? currentSeat.getSeatType().toUpperCase()
                                : "NORMAL";

                if ("SWEETBOX".equals(seatType)
                        || "COUPLE".equals(seatType)) {
                    continue;
                }

                Long currentId = currentSeat.getId();

                boolean isOccupied =
                        alreadyOccupied.contains(currentId);

                boolean isSelected =
                        newlySelected.contains(currentId);

                if (!isOccupied && !isSelected) {

                    int currentNum =
                            Integer.parseInt(currentSeat.getSeatNumber());

                    // LEFT
                    Seat leftSeat =
                            seatMapByNum.get(currentNum - 1);

                    boolean leftBlocked = false;
                    boolean leftSelected = false;

                    if (leftSeat != null) {

                        boolean leftOccupied =
                                alreadyOccupied.contains(leftSeat.getId());

                        boolean leftSimSelected =
                                newlySelected.contains(leftSeat.getId());

                        if (leftOccupied || leftSimSelected) {

                            leftBlocked = true;

                            if (leftSimSelected) {
                                leftSelected = true;
                            }
                        }
                    }

                    // RIGHT
                    Seat rightSeat =
                            seatMapByNum.get(currentNum + 1);

                    boolean rightBlocked = false;
                    boolean rightSelected = false;

                    if (rightSeat != null) {

                        boolean rightOccupied =
                                alreadyOccupied.contains(rightSeat.getId());

                        boolean rightSimSelected =
                                newlySelected.contains(rightSeat.getId());

                        if (rightOccupied || rightSimSelected) {

                            rightBlocked = true;

                            if (rightSimSelected) {
                                rightSelected = true;
                            }
                        }
                    }

                    if (leftBlocked && rightBlocked) {

                        if (leftSelected || rightSelected) {

                            throw new RuntimeException(
                                    "Không được để lại ghế trống đơn lẻ ("
                                            + currentSeat.getName()
                                            + ") ở giữa hàng ghế!"
                            );
                        }
                    }
                }
            }
        }
    }

    // ================= AUTO GENERATE =================
    @Override
    @Transactional
    public List<Seat> generateSeatsForRoom(
            Long roomId,
            int numRows,
            int seatsPerRow
    ) {

        validateRoomAccess(roomId);

        // Đã thay đổi logic: Chỉ chặn khi có suất chiếu ĐANG hoặc SẮP diễn ra
        if (hasActiveOrFutureShowtimes(roomId)) {
            throw new RuntimeException(
                    "Phòng đang có suất chiếu hoạt động hoặc sắp diễn ra, không thể chỉnh sửa sơ đồ ghế!"
            );
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Phòng không tồn tại"));

        int total = numRows * seatsPerRow;

        if (total > room.getTotalSeats()) {

            throw new RuntimeException(
                    "Vượt quá sức chứa phòng"
            );
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

        // Đã thay đổi logic
        if (hasActiveOrFutureShowtimes(request.getRoomId())) {
            throw new RuntimeException(
                    "Phòng đang có suất chiếu hoạt động hoặc sắp diễn ra, không thể thêm ghế!"
            );
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Phòng không tồn tại"));

        long count =
                seatRepository.countByRoomId(room.getId());

        if (count >= room.getTotalSeats()) {

            throw new RuntimeException(
                    "Phòng đã đầy, không thể thêm ghế mới"
            );
        }

        List<Seat> existingSeats =
                seatRepository.findByRoomId(room.getId());

        boolean isDuplicate = existingSeats.stream()
                .anyMatch(s ->
                        s.getSeatRow().equalsIgnoreCase(request.getSeatRow())
                                &&
                                s.getSeatNumber().equalsIgnoreCase(
                                        String.valueOf(request.getSeatNumber())
                                )
                );

        if (isDuplicate) {

            throw new RuntimeException(
                    "Vị trí ghế "
                            + request.getSeatRow()
                            + request.getSeatNumber()
                            + " đã tồn tại!"
            );
        }

        Seat seat = new Seat();

        seat.setRoom(room);
        seat.setStatus("AVAILABLE");

        mapRequestToEntity(request, seat, room);

        validateSweetboxPairing(
                room.getId(),
                seat,
                false
        );

        return seatRepository.save(seat);
    }

    // ================= UPDATE =================
    @Override
    @Transactional
    public Seat updateSeat(Long id, SeatRequest request) {

        Seat seat = seatRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Không tìm thấy ghế"));

        validateRoomAccess(seat.getRoom().getId());

        // Đã thay đổi logic
        if (hasActiveOrFutureShowtimes(seat.getRoom().getId())) {
            throw new RuntimeException(
                    "Phòng đang có suất chiếu hoạt động hoặc sắp diễn ra, không thể cập nhật ghế!"
            );
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Phòng không tồn tại"));

        List<Seat> existingSeats =
                seatRepository.findByRoomId(room.getId());

        boolean isDuplicate = existingSeats.stream()
                .anyMatch(s ->
                        !s.getId().equals(id)
                                &&
                                s.getSeatRow().equalsIgnoreCase(request.getSeatRow())
                                &&
                                s.getSeatNumber().equalsIgnoreCase(
                                        String.valueOf(request.getSeatNumber())
                                )
                );

        if (isDuplicate) {

            throw new RuntimeException(
                    "Vị trí ghế cập nhật đã bị trùng!"
            );
        }

        mapRequestToEntity(request, seat, room);

        if (request.getStatus() != null) {
            seat.setStatus(request.getStatus());
        }

        validateSweetboxPairing(
                room.getId(),
                seat,
                true
        );

        return seatRepository.save(seat);
    }

    // ================= SWEETBOX =================
    private void validateSweetboxPairing(
            Long roomId,
            Seat targetSeat,
            boolean isUpdate
    ) {

        if (!"SWEETBOX".equalsIgnoreCase(
                targetSeat.getSeatType()
        )) {
            return;
        }

        int seatNum =
                Integer.parseInt(targetSeat.getSeatNumber());

        int partnerNumber =
                (seatNum % 2 != 0)
                        ? (seatNum + 1)
                        : (seatNum - 1);

        List<Seat> existingSeats =
                seatRepository.findByRoomId(roomId);

        boolean hasPartner = existingSeats.stream()
                .anyMatch(s ->
                        s.getSeatRow().equalsIgnoreCase(
                                targetSeat.getSeatRow()
                        )
                                &&
                                s.getSeatNumber().equals(
                                        String.valueOf(partnerNumber)
                                )
                                &&
                                "SWEETBOX".equalsIgnoreCase(
                                        s.getSeatType()
                                )
                );

        if (!hasPartner && !isUpdate) {

            System.out.println(
                    "[Cảnh báo] Ghế Sweetbox "
                            + targetSeat.getName()
                            + " đang thiếu cặp!"
            );
        }
    }

    // ================= DELETE =================
    @Override
    @Transactional
    public void deleteSeat(Long id) {

        Seat seat = seatRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Ghế không tồn tại"));

        validateRoomAccess(seat.getRoom().getId());

        // Đã thay đổi logic
        if (hasActiveOrFutureShowtimes(seat.getRoom().getId())) {
            throw new RuntimeException(
                    "Phòng đang có suất chiếu hoạt động hoặc sắp diễn ra, không thể xóa ghế!"
            );
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

        if (isSuperAdmin(user)) {
            return seatRepository.findAll();
        }

        return seatRepository.findByRoom_CinemaItem_Id(
                user.getManagedCinemaItemId()
        );
    }

    @Override
    @Transactional
    public void deleteSeatsByRoom(Long roomId) {

        validateRoomAccess(roomId);

        // Đã thay đổi logic
        if (hasActiveOrFutureShowtimes(roomId)) {
            throw new RuntimeException(
                    "Phòng đang có suất chiếu hoạt động hoặc sắp diễn ra, không thể xóa toàn bộ ghế!"
            );
        }

        seatRepository.deleteByRoomId(roomId);
    }

    @Override
    public Map<String, Boolean> checkSeatEligibility(Long id) {

        Seat seat = seatRepository.findById(id).orElse(null);

        boolean canDelete = true;

        if (seat != null && seat.getRoom() != null) {
            // Đã thay đổi logic, truyền trạng thái chuẩn về cho Frontend
            if (hasActiveOrFutureShowtimes(seat.getRoom().getId())) {
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
    private void mapRequestToEntity(
            SeatRequest request,
            Seat seat,
            Room room
    ) {

        seat.setSeatRow(request.getSeatRow());

        seat.setSeatNumber(
                String.valueOf(request.getSeatNumber())
        );

        seat.setName(
                request.getSeatRow()
                        + request.getSeatNumber()
        );

        seat.setRoom(room);

        String type = request.getSeatType();

        if (type == null) {
            type = "NORMAL";
        }

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

    // ================= SECURITY =================
    private User getCurrentUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Phiên đăng nhập không tồn tại hoặc hết hạn"
                        ));
    }

    private boolean isSuperAdmin(User user) {

        return user.getRoles().stream()
                .anyMatch(r ->
                        r.getRoleName()
                                .toUpperCase()
                                .contains("ADMIN")
                );
    }

    private void validateRoomAccess(Long roomId) {

        User user = getCurrentUser();

        if (isSuperAdmin(user)) {
            return;
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Phòng không tồn tại"));

        if (room.getCinemaItem() == null
                ||
                !room.getCinemaItem()
                        .getId()
                        .equals(user.getManagedCinemaItemId())) {

            throw new RuntimeException(
                    "Bạn không có quyền quản lý phòng này!"
            );
        }
    }
}
