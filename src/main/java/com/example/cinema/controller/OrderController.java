package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.OrderRequest;
import com.example.cinema.entity.Order;
import com.example.cinema.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("isAuthenticated()") // Bất kỳ ai login rồi đều được đặt hàng
    public ResponseEntity<ApiResponse<Order>> createOrder(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(ApiResponse.<Order>builder()
                .status(201)
                .message("Đặt vé thành công!")
                .data(orderService.createOrder(request))
                .build());
    }

    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Order>>> getMyHistory() {
        return ResponseEntity.ok(ApiResponse.<List<Order>>builder()
                .status(200)
                .message("Lấy lịch sử mua vé thành công")
                .data(orderService.getMyOrders())
                .build());
    }

    @GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Order>>> getAllOrders() {
        return ResponseEntity.ok(ApiResponse.<List<Order>>builder()
                .status(200)
                .message("Admin lấy tất cả đơn hàng thành công")
                .data(orderService.getAllOrders())
                .build());
    }
}