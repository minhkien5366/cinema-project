package com.example.cinema.repository;

import com.example.cinema.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    // Tìm vé theo mã đặt chỗ
    Ticket findByBookingCode(String bookingCode);
    
    // Lấy lịch sử vé của một User
    List<Ticket> findByUserUserId(Long userId);
    
    // Kiểm tra xem một ghế trong một suất chiếu đã được mua chưa
    boolean existsBySeatIdAndShowtimeIdAndStatus(Long seatId, Long showtimeId, String status);
}