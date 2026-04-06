package com.example.cinema.repository;

import com.example.cinema.entity.Seat;
import com.example.cinema.entity.Showtime;
import com.example.cinema.entity.Ticket;
import com.example.cinema.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // 1. Tìm vé theo mã đặt chỗ
    Optional<Ticket> findByBookingCode(String bookingCode);
    
    // 2. Lấy lịch sử vé của một User dựa trên ID
    List<Ticket> findByUserUserId(Long userId);

    // 3. Lấy lịch sử vé của User dựa trên Email (Dùng cho Security)
    List<Ticket> findByUserEmailOrderByCreatedAtDesc(String email);

    // 4. Lấy danh sách tất cả vé của một suất chiếu
    List<Ticket> findByShowtimeId(Long showtimeId);

    // 5. Kiểm tra ghế đã có người đặt chưa
    boolean existsBySeatAndShowtime(Seat seat, Showtime showtime);

    // 6. Kiểm tra nâng cao: Ghế đã bị chiếm bởi các vé có trạng thái nằm trong danh sách truyền vào chưa
    boolean existsBySeatAndShowtimeAndStatusIn(Seat seat, Showtime showtime, List<String> statuses);

    // 7. Tìm chính xác vé dựa trên ghế, suất chiếu và người đặt (Dùng để đồng bộ trạng thái khi thanh toán)
    Optional<Ticket> findBySeatAndShowtimeAndUser(Seat seat, Showtime showtime, User user);

    // 8. Lấy danh sách CÁC GHẾ đã được đặt trong một suất chiếu - SỬA LẠI CÚ PHÁP CHUẨN @QUERY
    @Query("SELECT t.seat FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status IN ('BOOKED', 'PAID')")
    List<Seat> findOccupiedSeatsByShowtimeId(@Param("showtimeId") Long showtimeId);
}