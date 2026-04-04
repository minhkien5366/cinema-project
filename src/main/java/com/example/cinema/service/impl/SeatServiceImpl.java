package com.example.cinema.service.impl;

import com.example.cinema.dto.SeatRequest;
import com.example.cinema.entity.Room;
import com.example.cinema.entity.Seat;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.RoomRepository;
import com.example.cinema.repository.SeatRepository;
import com.example.cinema.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;

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
                
                String r = String.valueOf(rowLabel);
                String n = String.valueOf(j); // Hoặc String.format("%02d", j) nếu muốn A01
                
                seat.setSeatRow(r);
                seat.setSeatNumber(n);
                
                // GIẢI QUYẾT LỖI NULL: Tự tạo tên ghế (Ví dụ: A1, B10)
                seat.setName(r + n); 
                
                seat.setStatus("AVAILABLE");

                // Logic phân loại ghế: A, B là thường, còn lại là VIP
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
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ghế"));
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

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
        // Tự gán name dựa trên hàng và số
        seat.setName(request.getSeatRow() + request.getSeatNumber());
        seat.setSeatType(request.getSeatType());
        seat.setPrice(request.getPrice());
        seat.setRoom(room);
        if (seat.getStatus() == null) seat.setStatus("AVAILABLE");
    }
}