package com.example.cinema.repository;

import com.example.cinema.entity.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    // Lấy tất cả chi tiết (vé/combo) của một đơn hàng
    List<OrderDetail> findByOrderId(Long orderId);
}