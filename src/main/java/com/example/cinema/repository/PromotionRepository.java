package com.example.cinema.repository;

import com.example.cinema.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    // Lấy tin tức/sự kiện của rạp đó hoặc tin tức chung toàn hệ thống
    List<Promotion> findByCinemaItem_IdOrCinemaItemIsNull(Long cinemaItemId);
    
    // Dành cho Admin chi nhánh quản lý bài viết của mình
    List<Promotion> findByCinemaItem_Id(Long cinemaItemId);
}