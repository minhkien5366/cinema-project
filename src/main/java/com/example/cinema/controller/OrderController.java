package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.OrderRequest;
import com.example.cinema.dto.OrderResponse;
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@RequestBody OrderRequest request) {
        System.out.println("DEBUG - Dữ liệu JSON nhận được tại Controller: " + request);
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                .status(201)
                .message("Đặt vé thành công!")
                .data(response)
                .build());
    }

    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyHistory() {
        return ResponseEntity.ok(ApiResponse.<List<OrderResponse>>builder()
                .status(200)
                .message("Lấy lịch sử mua vé thành công")
                .data(orderService.getMyOrders())
                .build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        return ResponseEntity.ok(ApiResponse.<List<OrderResponse>>builder()
                .status(200)
                .message("Admin lấy tất cả đơn hàng thành công")
                .data(orderService.getAllOrders())
                .build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                .status(200)
                .message("Lấy chi tiết đơn hàng thành công")
                .data(orderService.getOrderById(id))
                .build());
    }

    // --- API CẬP NHẬT TRẠNG THÁI (MỚI THÊM) ---
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id, 
            @RequestParam String status) {
        
        OrderResponse updatedOrder = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                .status(200)
                .message("Cập nhật trạng thái đơn hàng sang " + status + " thành công!")
                .data(updatedOrder)
                .build());
    }
}