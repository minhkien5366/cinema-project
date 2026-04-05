package com.example.cinema.service.impl;

import com.example.cinema.dto.SeatRequest;
import com.example.cinema.entity.Room;
import com.example.cinema.entity.Seat;
import com.example.cinema.entity.Showtime;
import com.example.cinema.entity.Ticket;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.RoomRepository;
import com.example.cinema.repository.SeatRepository;
import com.example.cinema.repository.ShowtimeRepository;
import com.example.cinema.repository.TicketRepository;
import com.example.cinema.service.SeatService;
import lombok.RequiredArgsConstructor;
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

    @Override
    public List<Seat> getSeatsByShowtime(Long showtimeId) {
        // Log để Ngọc Trần kiểm tra trong Console IntelliJ
        System.out.println(">>> [LOG] Đang truy vấn ghế cho Suất chiếu ID: " + showtimeId);

        // 1. Tìm suất chiếu để xác định phòng chiếu
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu ID: " + showtimeId));

        if (showtime.getRoom() == null) {
            System.out.println(">>> [CẢNH BÁO] Suất chiếu ID " + showtimeId + " chưa được gán phòng!");
            return new ArrayList<>();
        }

        Long roomId = showtime.getRoom().getId();
        System.out.println(">>> [LOG] Suất chiếu thuộc Phòng ID: " + roomId);

        // 2. Lấy tất cả ghế nguyên bản thuộc phòng đó
        List<Seat> allSeatsInRoom = seatRepository.findByRoomId(roomId);
        System.out.println(">>> [LOG] Tìm thấy " + allSeatsInRoom.size() + " ghế trong CSDL cho phòng này.");

        // 3. Lấy danh sách ID ghế đã có vé (Ticket) trong suất chiếu này
        List<Ticket> tickets = ticketRepository.findByShowtimeId(showtimeId);
        Set<Long> occupiedSeatIds = tickets.stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus())) // Loại bỏ vé đã hủy
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        // 4. Gán trạng thái OCCUPIED cho ghế đã bán (chỉ trả về Client, không lưu xuống bảng seats)
        for (Seat seat : allSeatsInRoom) {
            if (occupiedSeatIds.contains(seat.getId())) {
                seat.setStatus("OCCUPIED");
            } else {
                seat.setStatus("AVAILABLE");
            }
        }
        return allSeatsInRoom;
    }

    @Override
    public List<Seat> getAllSeats() {
        return seatRepository.findAll();
    }

    @Override
    public List<Seat> getSeatsByRoom(Long roomId) {
        return seatRepository.findByRoomId(roomId);
    }

    @Override
    @Transactional
    public Seat createSeat(SeatRequest request) {
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));
        Seat seat = new Seat();
        mapRequestToEntity(request, seat, room);
        return seatRepository.save(seat);
    }

    @Override
    @Transactional
    public List<Seat> generateSeatsForRoom(Long roomId, int numRows, int seatsPerRow) {
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
                seat.setName(String.valueOf(rowLabel) + j); 
                seat.setStatus("AVAILABLE");
                if (rowLabel <= 'B') {
                    seat.setSeatType("NORMAL");
                    seat.setPrice(80000.0);
                } else {
                    seat.setSeatType("VIP");
                    seat.setPrice(120000.0);
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
        Seat seat = seatRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ghế"));
        Room room = roomRepository.findById(request.getRoomId()).orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));
        mapRequestToEntity(request, seat, room);
        if (request.getStatus() != null) seat.setStatus(request.getStatus());
        return seatRepository.save(seat);
    }

    @Override
    @Transactional
    public void deleteSeat(Long id) {
        seatRepository.deleteById(id);
    }

    private void mapRequestToEntity(SeatRequest request, Seat seat, Room room) {
        seat.setSeatRow(request.getSeatRow());
        seat.setSeatNumber(request.getSeatNumber());
        seat.setName(request.getSeatRow() + request.getSeatNumber());
        seat.setSeatType(request.getSeatType());
        seat.setPrice(request.getPrice());
        seat.setRoom(room);
        if (seat.getStatus() == null) seat.setStatus("AVAILABLE");
    }
}