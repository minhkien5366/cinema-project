package com.example.cinema.service;

import com.example.cinema.dto.SeatRequest;
import com.example.cinema.entity.Seat;
import java.util.List;

public interface SeatService {
    List<Seat> getAllSeats();
    List<Seat> getSeatsByRoom(Long roomId);
    List<Seat> getSeatsByShowtime(Long showtimeId); 
    Seat createSeat(SeatRequest request);
    List<Seat> generateSeatsForRoom(Long roomId, int rows, int seatsPerRow);
    Seat updateSeat(Long id, SeatRequest request);
    void deleteSeat(Long id);
}