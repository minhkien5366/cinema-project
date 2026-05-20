package com.example.cinema.repository;

import com.example.cinema.entity.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

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

    @Query("SELECT o FROM Order o WHERE o.status = 'PAID'")
    Stream<Order> streamAllPaidOrders();
    List<Order> findByCinemaItemIdAndCreatedAtBetweenAndStatus(
    Long cinemaItemId, 
    LocalDateTime start, 
    LocalDateTime end, 
    String status
);

@Query("SELECT o.cinemaItem.name, SUM(o.totalAmount) FROM Order o " +
       "WHERE o.createdAt BETWEEN :start AND :end AND o.status = 'PAID' " +
       "GROUP BY o.cinemaItem.name ORDER BY SUM(o.totalAmount) DESC")
List<Object[]> getCinemaRanking(LocalDateTime start, LocalDateTime end);
}