package com.example.cinema.repository;

import com.example.cinema.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    // Thêm hàm này để khớp với Service
    List<Room> findByCinemaItem_Id(Long cinemaItemId);
    Optional<Room> findByName(String name);
    Optional<Room> findByNameAndCinemaItemId(String name, Long cinemaItemId);
}