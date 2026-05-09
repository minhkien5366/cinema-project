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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final ShowtimeRepository showtimeRepository; 
    private final TicketRepository ticketRepository;     
    private final UserRepository userRepository;

    private static final double PRICE_NORMAL = 80000.0;
    private static final double PRICE_VIP = 120000.0;
    private static final double PRICE_COUPLE = 250000.0;

    @Override
    public List<Seat> getSeatsByShowtime(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu ID: " + showtimeId));
        
        if (showtime.getRoom() == null) return new ArrayList<>();

        List<Seat> allSeatsInRoom = seatRepository.findByRoomId(showtime.getRoom().getId());
        List<Ticket> tickets = ticketRepository.findByShowtimeId(showtimeId);
        
        Set<Long> occupiedSeatIds = tickets.stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()))
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        for (Seat seat : allSeatsInRoom) {
            seat.setStatus(occupiedSeatIds.contains(seat.getId()) ? "OCCUPIED" : "AVAILABLE");
        }
        return allSeatsInRoom;
    }

    @Override
    @Transactional
    public List<Seat> generateSeatsForRoom(Long roomId, int numRows, int seatsPerRow) {
        validateRoomAccess(roomId); 
        
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        int totalToGenerate = numRows * seatsPerRow;
        if (totalToGenerate > room.getTotalSeats()) {
            throw new RuntimeException("Số ghế tạo ra (" + totalToGenerate + 
                ") vượt quá sức chứa của phòng (" + room.getTotalSeats() + ")");
        }

        if (ticketRepository.existsBySeat_Room_Id(roomId)) {
            throw new RuntimeException("Không thể reset sơ đồ vì phòng này đã có lịch sử đặt vé!");
        }

        seatRepository.deleteByRoomId(roomId);
        
        List<Seat> seats = new ArrayList<>();
        char rowLabel = 'A';

        for (int i = 0; i < numRows; i++) {
            for (int j = 1; j <= seatsPerRow; j++) {
                Seat seat = new Seat();
                seat.setRoom(room);
                seat.setSeatRow(String.valueOf(rowLabel));
                seat.setSeatNumber(String.valueOf(j));
                seat.setName(rowLabel + String.valueOf(j)); 
                seat.setStatus("AVAILABLE");

                // Phân loại ghế tự động
                if (i == numRows - 1) { 
                    seat.setSeatType("COUPLE");
                    seat.setPrice(PRICE_COUPLE);
                } else if (i >= 2 && i <= numRows - 3) { 
                    seat.setSeatType("VIP");
                    seat.setPrice(PRICE_VIP);
                } else { 
                    seat.setSeatType("NORMAL");
                    seat.setPrice(PRICE_NORMAL);
                }
                seats.add(seat);
            }
            rowLabel++;
        }
        return seatRepository.saveAll(seats);
    }

    @Override
    @Transactional
    public Seat createSeat(SeatRequest request) {
        validateRoomAccess(request.getRoomId());
        
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        // KIỂM TRA: Sức chứa trước khi thêm ghế lẻ
        long currentSeatCount = seatRepository.countByRoomId(room.getId());
        if (currentSeatCount >= room.getTotalSeats()) {
            throw new RuntimeException("Không thể thêm ghế. Phòng đã đạt giới hạn tối đa " + room.getTotalSeats() + " ghế.");
        }

        Seat seat = new Seat();
        seat.setStatus("AVAILABLE");
        mapRequestToEntity(request, seat, room);
        return seatRepository.save(seat);
    }

    @Override
    @Transactional
    public Seat updateSeat(Long id, SeatRequest request) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ghế"));
        
        validateRoomAccess(seat.getRoom().getId());

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        mapRequestToEntity(request, seat, room);
        if (request.getStatus() != null) seat.setStatus(request.getStatus());
        
        return seatRepository.save(seat);
    }

    @Override
    @Transactional
    public void deleteSeat(Long id) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));
        
        validateRoomAccess(seat.getRoom().getId());

        // KIỂM TRA: Ràng buộc khóa ngoại (Vé)
        if (ticketRepository.existsBySeatId(id)) {
            throw new RuntimeException("Không thể xóa ghế vì đã có lịch sử đặt vé liên quan.");
        }

        seatRepository.deleteById(id);
    }

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
        if (ticketRepository.existsBySeat_Room_Id(roomId)) {
            throw new RuntimeException("Không thể xóa danh sách ghế vì phòng đã có vé được bán!");
        }
        seatRepository.deleteByRoomId(roomId);
    }

    // --- HELPER METHODS ---

    private void mapRequestToEntity(SeatRequest request, Seat seat, Room room) {
        seat.setSeatRow(request.getSeatRow());
        seat.setSeatNumber(String.valueOf(request.getSeatNumber()));
        seat.setName(request.getSeatRow() + request.getSeatNumber());
        seat.setSeatType(request.getSeatType().toUpperCase());
        seat.setRoom(room);

        if (request.getPrice() != null && request.getPrice() > 0) {
            seat.setPrice(request.getPrice());
        } else {
            switch (request.getSeatType().toUpperCase()) {
                case "VIP": seat.setPrice(PRICE_VIP); break;
                case "COUPLE": seat.setPrice(PRICE_COUPLE); break;
                default: seat.setPrice(PRICE_NORMAL); break;
            }
        }
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equalsIgnoreCase("SUPER_ADMIN") 
                            || r.getRoleName().equalsIgnoreCase("ROLE_SUPER_ADMIN"));
    }

    private void validateRoomAccess(Long roomId) {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return;

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại ID: " + roomId));

        if (room.getCinemaItem() == null || !room.getCinemaItem().getId().equals(user.getManagedCinemaItemId())) {
            throw new RuntimeException("Bạn không có quyền quản lý ghế tại chi nhánh rạp này!");
        }
    }
}