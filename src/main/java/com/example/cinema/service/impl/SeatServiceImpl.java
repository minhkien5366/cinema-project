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

    // ================= PRICE =================
    private static final double PRICE_NORMAL = 80000.0;
    private static final double PRICE_VIP = 120000.0;
    private static final double PRICE_SWEETBOX = 250000.0;

    // ================= SHOWTIME =================
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

        for (Seat s : seats) {
            s.setStatus(occupied.contains(s.getId()) ? "OCCUPIED" : "AVAILABLE");
        }

        return seats;
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

        // ⚠️ FIX: chỉ xoá ghế, không check ticket toàn phòng
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
            throw new RuntimeException("Phòng đã đầy");
        }

        Seat seat = new Seat();
        seat.setRoom(room);
        seat.setStatus("AVAILABLE");

        mapRequestToEntity(request, seat, room);

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

        mapRequestToEntity(request, seat, room);

        if (request.getStatus() != null) {
            seat.setStatus(request.getStatus());
        }

        return seatRepository.save(seat);
    }

    // ================= DELETE =================
    @Override
    @Transactional
    public void deleteSeat(Long id) {

        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));

        validateRoomAccess(seat.getRoom().getId());

        // FIX: check đúng ghế này có vé hay không
        if (ticketRepository.existsBySeatId(id)) {
            throw new RuntimeException("Ghế đã có vé, không thể xóa");
        }

        seatRepository.deleteById(id);
    }

    // ================= ROOM =================
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

    // ================= USER =================
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
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
            throw new RuntimeException("Không có quyền truy cập");
        }
    }
}