package com.example.cinema.repository;

import com.example.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
    List<Showtime> findByMovieId(Long movieId);
    List<Showtime> findByCinemaItem_Id(Long cinemaItemId);
    List<Showtime> findByRoomId(Long roomId);

    // Kiểm tra trùng lịch kèm buffer 20 phút dọn phòng
    @Query("SELECT s FROM Showtime s WHERE s.room.id = :roomId " +
           "AND (:newStart < s.endTime OR :newEnd > s.startTime) " +
           "AND NOT (s.endTime <= :startWithBuffer OR s.startTime >= :endWithBuffer)")
    List<Showtime> findOverlappingShowtimes(
            @Param("roomId") Long roomId,
            @Param("newStart") LocalDateTime newStart,
            @Param("newEnd") LocalDateTime newEnd,
            @Param("startWithBuffer") LocalDateTime startWithBuffer,
            @Param("endWithBuffer") LocalDateTime endWithBuffer);

    @Query("SELECT s FROM Showtime s WHERE s.movie.id = :movieId " +
           "AND CAST(s.startTime AS date) = :date")
    List<Showtime> findByMovieIdAndDate(@Param("movieId") Long movieId, @Param("date") LocalDate date);
}