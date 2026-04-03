package com.example.cinema.service;

import com.example.cinema.dto.SeatRequest;
import com.example.cinema.entity.Seat;
import java.util.List;

public interface SeatService {
    List<Seat> getAllSeats();
    List<Seat> getSeatsByRoom(Long roomId);
    Seat createSeat(SeatRequest request);
    Seat updateSeat(Long id, SeatRequest request);
    void deleteSeat(Long id);

    // Hàm sinh ghế tự động theo số hàng và số ghế mỗi hàng
    List<Seat> generateSeatsForRoom(Long roomId, int numRows, int seatsPerRow);
}