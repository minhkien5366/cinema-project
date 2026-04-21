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

    // Giá vé mặc định cho từng loại (Duy có thể chỉnh sửa số này)
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

                // LOGIC PHÂN LOẠI GHẾ TỰ ĐỘNG
                if (i == numRows - 1) { 
                    // Hàng cuối cùng là ghế đôi
                    seat.setSeatType("COUPLE");
                    seat.setPrice(PRICE_COUPLE);
                } else if (i >= 2 && i <= numRows - 3) { 
                    // Các hàng ở giữa là VIP
                    seat.setSeatType("VIP");
                    seat.setPrice(PRICE_VIP);
                } else { 
                    // Các hàng còn lại là thường
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
    public Seat updateSeat(Long id, SeatRequest request) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ghế"));
        
        validateRoomAccess(seat.getRoom().getId());
        if (!seat.getRoom().getId().equals(request.getRoomId())) {
            validateRoomAccess(request.getRoomId());
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        // Cập nhật thông tin và tự động áp giá nếu người dùng không nhập giá cụ thể
        mapRequestToEntity(request, seat, room);
        
        if (request.getStatus() != null) seat.setStatus(request.getStatus());
        
        return seatRepository.save(seat);
    }

    @Override
    public List<Seat> getAllSeats() {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return seatRepository.findAll();
        return seatRepository.findByRoom_CinemaItem_Id(user.getManagedCinemaItemId());
    }

    @Override
    public List<Seat> getSeatsByRoom(Long roomId) {
        validateRoomAccess(roomId);
        return seatRepository.findByRoomId(roomId);
    }

    @Override
    @Transactional
    public Seat createSeat(SeatRequest request) {
        validateRoomAccess(request.getRoomId());
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));
        Seat seat = new Seat();
        mapRequestToEntity(request, seat, room);
        return seatRepository.save(seat);
    }

    @Override
    @Transactional
    public void deleteSeat(Long id) {
        Seat seat = seatRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));
        validateRoomAccess(seat.getRoom().getId());
        seatRepository.deleteById(id);
    }

    // --- HELPER METHODS ---

    private void mapRequestToEntity(SeatRequest request, Seat seat, Room room) {
        seat.setSeatRow(request.getSeatRow());
        seat.setSeatNumber(request.getSeatNumber());
        seat.setName(request.getSeatRow() + request.getSeatNumber());
        seat.setSeatType(request.getSeatType().toUpperCase());
        seat.setRoom(room);

        // TỰ ĐỘNG CẬP NHẬT GIÁ THEO LOẠI NẾU KHÔNG NHẬP GIÁ RIÊNG
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
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
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