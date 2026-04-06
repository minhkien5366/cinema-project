package com.example.cinema.service;

import com.example.cinema.dto.OrderRequest;
import com.example.cinema.dto.OrderResponse;
import java.util.List;

public interface OrderService {
    // Tạo đơn hàng mới
    OrderResponse createOrder(OrderRequest request);
    
    // Lấy chi tiết 1 đơn hàng
    OrderResponse getOrderById(Long id);
    
    // Xem lịch sử mua vé của người đang đăng nhập
    List<OrderResponse> getMyOrders();
    
    // Admin xem tất cả đơn hàng
    List<OrderResponse> getAllOrders();

    // Cập nhật trạng thái đơn hàng (PAID, CANCELLED,...) - MỚI THÊM
    OrderResponse updateOrderStatus(Long orderId, String newStatus);
}