package com.example.cinema.repository;

import com.example.cinema.entity.CinemaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CinemaItemRepository extends JpaRepository<CinemaItem, Long> {
    // Lấy tất cả chi nhánh thuộc một cụm rạp
    List<CinemaItem> findByCinemaId(Long cinemaId);
    
    // Tìm rạp theo thành phố
    List<CinemaItem> findByCity(String city);
}