package com.example.cinema.repository;

import com.example.cinema.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    // Thêm hàm này để khớp với Service
    List<Room> findByCinemaItem_Id(Long cinemaItemId);
}