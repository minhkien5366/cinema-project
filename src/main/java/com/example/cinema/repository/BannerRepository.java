package com.example.cinema.repository;

import com.example.cinema.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    // Lấy banner đang hoạt động và sắp xếp theo thứ tự hiển thị
    List<Banner> findByStatusOrderBySortOrderAsc(String status);
}