package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.OrderRequest;
import com.example.cinema.dto.OrderResponse;
import com.example.cinema.service.OrderService;
import jakarta.validation.Valid; // Thêm để kiểm tra Validation
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Hỗ trợ gọi API từ Frontend khác port
public class OrderController {

    private final OrderService orderService;

    /**
     * Tạo đơn hàng mới (Đặt vé + Combo)
     * Dành cho khách hàng đã đăng nhập
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody OrderRequest request) {
        // @Valid sẽ tự động kiểm tra các ràng buộc trong OrderRequest Duy đã viết
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(201).body(ApiResponse.<OrderResponse>builder()
                .status(201)
                .message("Đặt đơn hàng thành công!")
                .data(response)
                .build());
    }

    /**
     * Lấy lịch sử đặt vé của cá nhân
     */
    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyHistory() {
        return ResponseEntity.ok(ApiResponse.<List<OrderResponse>>builder()
                .status(200)
                .message("Lấy lịch sử mua vé thành công")
                .data(orderService.getMyOrders())
                .build());
    }

    /**
     * Lấy danh sách đơn hàng theo quyền hạn (Admin chi nhánh / Super Admin)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        // Service sẽ tự lọc: Super Admin thấy hết, Admin chi nhánh thấy rạp mình
        return ResponseEntity.ok(ApiResponse.<List<OrderResponse>>builder()
                .status(200)
                .message("Lấy danh sách đơn hàng thành công")
                .data(orderService.getAllOrders())
                .build());
    }

    /**
     * Lấy chi tiết một đơn hàng cụ thể
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                .status(200)
                .message("Lấy chi tiết đơn hàng thành công")
                .data(orderService.getOrderById(id))
                .build());
    }

    /**
     * Cập nhật trạng thái đơn hàng (Xác nhận thanh toán / Hủy đơn)
     * Dành cho Admin quản lý rạp
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id, 
            @RequestParam String status) {
        
        // Logic đồng bộ trạng thái Vé (Ticket) nằm bên trong hàm updateOrderStatus của Service
        OrderResponse updatedOrder = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                .status(200)
                .message("Cập nhật trạng thái đơn hàng sang " + status.toUpperCase() + " thành công!")
                .data(updatedOrder)
                .build());
    }
}