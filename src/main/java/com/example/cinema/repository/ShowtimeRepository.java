package com.example.cinema.repository;

import com.example.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
    List<Showtime> findByMovieId(Long movieId);
    List<Showtime> findByCinemaItemId(Long cinemaItemId);
    
    // HÀM MỚI: Lọc theo Phim và chính xác Ngày (bỏ qua Giờ)
    @Query("SELECT s FROM Showtime s WHERE s.movie.id = :movieId " +
           "AND FUNCTION('DATE', s.startTime) = :date")
    List<Showtime> findByMovieIdAndDate(@Param("movieId") Long movieId, @Param("date") LocalDate date);
}