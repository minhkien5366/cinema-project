package com.example.cinema.repository;

import com.example.cinema.entity.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // Lấy đơn hàng của người dùng
    List<Order> findByUserEmail(String email, Sort sort);

    // Lấy đơn hàng theo chi nhánh (Dành cho Admin chi nhánh)
    List<Order> findByCinemaItem_Id(Long cinemaItemId, Sort sort);
}