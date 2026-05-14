package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.OrderRequest;
import com.example.cinema.dto.OrderResponse;
import com.example.cinema.service.OrderService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;

    // Đường dẫn trang Frontend mà ông muốn chuyển về sau khi thanh toán
    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

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
     * Dùng HttpServletResponse để chuyển hướng trình duyệt về Frontend
     */
    @GetMapping("/vnpay-callback")
    public void vnpayCallback(@RequestParam Map<String, String> params, HttpServletResponse response) throws IOException {
        String responseCode = params.get("vnp_ResponseCode");
        String txnRef = params.get("vnp_TxnRef"); // Đây chính là Order ID
        String amount = params.get("vnp_Amount"); // Lấy thêm số tiền để Frontend hiển thị

        Long orderId = Long.parseLong(txnRef);

        // 1. Cập nhật trạng thái vào Database
        if ("00".equals(responseCode)) {
            // Thanh toán thành công -> Cập nhật PAID
            orderService.updateOrderStatus(orderId, "PAID");
        } else {
            // Thanh toán thất bại hoặc hủy -> Cập nhật CANCELLED
            orderService.updateOrderStatus(orderId, "CANCELLED");
        }

        // 2. Chuyển hướng trình duyệt về trang Frontend (PaymentResultPage)
        // Kèm theo các tham số để Frontend biết là thành công hay thất bại
        String redirectUrl = frontendUrl + "/booking/payment/result"
                + "?vnp_ResponseCode=" + responseCode
                + "&vnp_TxnRef=" + txnRef
                + "&vnp_Amount=" + (amount != null ? amount : "0");

        // Gửi lệnh Redirect (Mã 302)
        response.sendRedirect(redirectUrl);
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