package com.example.cinema.repository;

import com.example.cinema.entity.Seat;
import com.example.cinema.entity.Showtime;
import com.example.cinema.entity.Ticket;
import com.example.cinema.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByBookingCode(String bookingCode);
    
    List<Ticket> findByUserUserId(Long userId);

    List<Ticket> findByUserEmailOrderByCreatedAtDesc(String email);

    List<Ticket> findByShowtimeId(Long showtimeId);

    List<Ticket> findByShowtime_CinemaItem_Id(Long cinemaItemId, Sort sort);

    boolean existsBySeatAndShowtime(Seat seat, Showtime showtime);

    boolean existsBySeatAndShowtimeAndStatusIn(Seat seat, Showtime showtime, List<String> statuses);

    Optional<Ticket> findBySeatAndShowtimeAndUser(Seat seat, Showtime showtime, User user);

    boolean existsBySeatId(Long seatId);

    boolean existsBySeat_Room_Id(Long roomId);

    // --- FIX LỖI 1: Ràng buộc đã xem xong phim ---
    boolean existsByUser_UserIdAndShowtime_Movie_IdAndStatusAndShowtime_EndTimeBefore(
        Long userId, Long movieId, String status, LocalDateTime now);

    // --- FIX LỖI 2: Ràng buộc đã thanh toán vé (Dù phim chưa chiếu) ---
    boolean existsByUser_UserIdAndShowtime_Movie_IdAndStatus(Long userId, Long movieId, String status);

    Optional<Ticket> findBySeatIdAndShowtimeId(Long seatId, Long showtimeId);
    boolean existsBySeatAndShowtimeAndStatusNotIgnoreCase(Seat seat, Showtime showtime, String status);

    @Query("SELECT t.seat FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status IN ('BOOKED', 'PAID')")
    List<Seat> findOccupiedSeatsByShowtimeId(@Param("showtimeId") Long showtimeId);
}