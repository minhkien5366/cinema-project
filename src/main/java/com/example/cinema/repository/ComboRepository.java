package com.example.cinema.repository;

import com.example.cinema.entity.Combo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComboRepository extends JpaRepository<Combo, Long> {
    // Lấy danh sách combo giá rẻ hoặc theo tên
    List<Combo> findByPriceLessThan(Double price);
}