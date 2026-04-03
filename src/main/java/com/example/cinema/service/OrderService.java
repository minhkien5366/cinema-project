package com.example.cinema.service;

import com.example.cinema.dto.OrderRequest;
import com.example.cinema.entity.Order;
import java.util.List;

public interface OrderService {
    Order createOrder(OrderRequest request);
    Order getOrderById(Long id);
    List<Order> getMyOrders(); // Xem lịch sử của tôi
    List<Order> getAllOrders(); // Admin quản lý tất cả
}