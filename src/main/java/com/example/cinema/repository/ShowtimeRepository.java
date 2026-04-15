package com.example.cinema.repository;

import com.example.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    List<Showtime> findByMovieId(Long movieId);

    List<Showtime> findByCinemaItem_Id(Long cinemaItemId);

    List<Showtime> findByRoomId(Long roomId);

    @Query("SELECT s FROM Showtime s WHERE s.movie.id = :movieId " +
           "AND CAST(s.startTime AS date) = :date")
    List<Showtime> findByMovieIdAndDate(@Param("movieId") Long movieId, @Param("date") LocalDate date);
}