package com.example.cinema.repository;

import com.example.cinema.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {
    // Tìm kiếm theo tên và phân trang
    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    
    // Lọc theo trạng thái và phân trang
    Page<Movie> findByStatus(String status, Pageable pageable);
}