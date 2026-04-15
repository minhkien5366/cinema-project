package com.example.cinema.repository;

import com.example.cinema.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByRoomId(Long roomId); 

    // Tìm tất cả ghế thuộc một chi nhánh rạp (Dành cho Admin chi nhánh)
    List<Seat> findByRoom_CinemaItem_Id(Long cinemaItemId);
}