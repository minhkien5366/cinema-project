package com.example.cinema.repository;

import com.example.cinema.entity.Seat;
import com.example.cinema.entity.Showtime;
import com.example.cinema.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // 1. Tìm vé theo mã đặt chỗ (Dùng Optional để an toàn hơn khi khách nhập sai mã)
    Optional<Ticket> findByBookingCode(String bookingCode);
    
    // 2. Lấy lịch sử vé của một User dựa trên ID
    List<Ticket> findByUserUserId(Long userId);

    // 3. Lấy lịch sử vé của User dựa trên Email (Rất tiện khi dùng Spring Security)
    List<Ticket> findByUserEmailOrderByCreatedAtDesc(String email);

    // 4. Lấy danh sách tất cả vé của một suất chiếu (Để Frontend biết ghế nào đã bán mà bôi đỏ)
    List<Ticket> findByShowtimeId(Long showtimeId);

    // 5. Kiểm tra ghế đã có người đặt chưa (Truyền trực tiếp Object)
    boolean existsBySeatAndShowtime(Seat seat, Showtime showtime);

    // 6. Kiểm tra ghế đã có người đặt chưa kèm trạng thái (Ví dụ: Chỉ chặn nếu status là 'PAID')
    boolean existsBySeatIdAndShowtimeIdAndStatus(Long seatId, Long showtimeId, String status);

    // 7. Kiểm tra nâng cao: Ghế đã bị chiếm bởi các vé có trạng thái 'PAID' hoặc 'PENDING' chưa
    boolean existsBySeatAndShowtimeAndStatusIn(Seat seat, Showtime showtime, List<String> statuses);
    List<Seat> getSeatsByShowtime(Long showtimeId); // Hàm mới
}