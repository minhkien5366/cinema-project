package com.example.cinema.repository;

import com.example.cinema.entity.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Lấy đơn hàng của người dùng
    List<Order> findByUserEmail(String email, Sort sort);

    // Lấy đơn hàng theo chi nhánh (Dùng cho Admin chi nhánh)
    List<Order> findByCinemaItem_Id(Long cinemaItemId, Sort sort);

    // --- THÊM MỚI: Truy vấn ID Suất chiếu từ một Đơn hàng (Dùng để đồng bộ Ticket) ---
    @Query("SELECT DISTINCT t.showtime.id FROM Ticket t " +
           "JOIN OrderDetail od ON t.seat.id = od.itemId " +
           "WHERE od.order.id = :orderId AND od.itemType = 'TICKET'")
    Long findShowtimeIdByOrderId(@Param("orderId") Long orderId);
}