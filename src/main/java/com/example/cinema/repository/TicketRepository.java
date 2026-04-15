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

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // 1. Tìm vé theo mã đặt chỗ (Dùng để soát vé tại rạp)
    Optional<Ticket> findByBookingCode(String bookingCode);
    
    // 2. Lấy lịch sử vé của một User dựa trên ID
    List<Ticket> findByUserUserId(Long userId);

    // 3. Lấy lịch sử vé của User dựa trên Email (Dùng cho trang Profile khách hàng)
    List<Ticket> findByUserEmailOrderByCreatedAtDesc(String email);

    // 4. Lấy danh sách tất cả vé của một suất chiếu cụ thể
    List<Ticket> findByShowtimeId(Long showtimeId);

    // --- LOGIC QUAN TRỌNG: PHÂN QUYỀN ADMIN CHI NHÁNH ---
    // 5. Lấy toàn bộ vé thuộc về một chi nhánh rạp cụ thể (Dành cho Admin chi nhánh)
    // Spring sẽ tự Join: Ticket -> Showtime -> CinemaItem -> Id
    List<Ticket> findByShowtime_CinemaItem_Id(Long cinemaItemId, Sort sort);

    // 6. Kiểm tra ghế đã có người đặt chưa
    boolean existsBySeatAndShowtime(Seat seat, Showtime showtime);

    // 7. Kiểm tra ghế đã bị chiếm bởi các vé có trạng thái nằm trong danh sách truyền vào chưa (BOOKED, PAID)
    boolean existsBySeatAndShowtimeAndStatusIn(Seat seat, Showtime showtime, List<String> statuses);

    // 8. Tìm chính xác vé dựa trên ghế, suất chiếu và người đặt
    Optional<Ticket> findBySeatAndShowtimeAndUser(Seat seat, Showtime showtime, User user);

    // 9. Lấy danh sách CÁC GHẾ đã được đặt trong một suất chiếu
    @Query("SELECT t.seat FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status IN ('BOOKED', 'PAID')")
    List<Seat> findOccupiedSeatsByShowtimeId(@Param("showtimeId") Long showtimeId);
}