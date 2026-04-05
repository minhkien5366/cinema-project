package com.example.cinema.repository;

import com.example.cinema.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    // PHẢI CÓ DÒNG NÀY:
    List<Seat> findByRoomId(Long roomId); 
}