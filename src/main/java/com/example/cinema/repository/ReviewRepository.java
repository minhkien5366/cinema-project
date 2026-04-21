package com.example.cinema.repository;

import com.example.cinema.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    
    List<Review> findByMovieIdOrderByCreatedAtDesc(Long movieId);

    // Tính điểm trung bình rating của một bộ phim
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.movie.id = :movieId")
    Double getAverageRatingByMovieId(@Param("movieId") Long movieId);

    // FIX LỖI: Thêm dấu gạch dưới để truy cập đúng trường userId của Entity User
    boolean existsByUser_UserIdAndMovieId(Long userId, Long movieId);
}