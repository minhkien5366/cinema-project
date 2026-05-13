package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.OrderRequest;
import com.example.cinema.dto.OrderResponse;
import com.example.cinema.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(201).body(ApiResponse.<OrderResponse>builder()
                .status(201)
                .message("Đặt đơn hàng thành công!")
                .data(response)
                .build());
    }

    /**
     * Endpoint nhận phản hồi từ VNPAY
     * Không dùng @PreAuthorize vì VNPAY gọi về tự động
     */
    @GetMapping("/vnpay-callback")
    public ResponseEntity<ApiResponse<String>> vnpayCallback(@RequestParam Map<String, String> params) {
        String responseCode = params.get("vnp_ResponseCode");
        String txnRef = params.get("vnp_TxnRef"); // Đây chính là Order ID
        Long orderId = Long.parseLong(txnRef);

        if ("00".equals(responseCode)) {
            // Thanh toán thành công -> Cập nhật PAID
            // Lưu ý: Duy nên dùng một hàm update riêng không check quyền Admin cho hệ thống tự động
            orderService.updateOrderStatus(orderId, "PAID");
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .status(200)
                    .message("Thanh toán thành công!")
                    .data("Order #" + orderId + " đã được xác nhận thanh toán.")
                    .build());
        } else {
            // Thanh toán thất bại
            orderService.updateOrderStatus(orderId, "CANCELLED");
            return ResponseEntity.status(400).body(ApiResponse.<String>builder()
                    .status(400)
                    .message("Giao dịch thất bại hoặc đã bị hủy.")
                    .build());
        }
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
                .message("Lấy danh sách đơn hàng thành công")
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

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id, 
            @RequestParam String status) {
        OrderResponse updatedOrder = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                .status(200)
                .message("Cập nhật trạng thái thành công!")
                .data(updatedOrder)
                .build());
    }
}