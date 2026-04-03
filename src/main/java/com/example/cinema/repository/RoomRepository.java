package com.example.cinema.repository;

import com.example.cinema.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    // Lấy danh sách phòng của một chi nhánh rạp
    List<Room> findByCinemaItemId(Long cinemaItemId);
}