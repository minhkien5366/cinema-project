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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 🔥 Cho phép ứng dụng qr-app (port 3001) gọi sang thoải mái không bị chặn CORS
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
     * Endpoint nhận phản hồi tự động từ hệ thống VNPAY
     */
    @GetMapping("/vnpay-callback")
    public void vnpayCallback(@RequestParam Map<String, String> params, HttpServletResponse response) throws IOException {
        String responseCode = params.get("vnp_ResponseCode");
        String txnRef = params.get("vnp_TxnRef"); // Đây chính là Order ID
        String amount = params.get("vnp_Amount"); // Lấy thêm số tiền để Frontend hiển thị

        Long orderId = Long.parseLong(txnRef);

        // Cập nhật trạng thái vào Database
        if ("00".equals(responseCode)) {
            orderService.updateOrderStatus(orderId, "PAID");
        } else {
            orderService.updateOrderStatus(orderId, "CANCELLED");
        }

        // Chuyển hướng trình duyệt về trang kết quả Frontend khách hàng
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

    // ================= 🔥 ENDPOINT 1: XỬ LÝ KIỂM TRA MÃ QR TỪ CAMERA =================
    @GetMapping("/scan")
    // @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") // Tạm khóa dòng check quyền này lại để ông test local không bị lỗi 403 nhé
    public ResponseEntity<ApiResponse<OrderResponse>> scanQrCode(@RequestParam String qrContent) {
        
        // 1. Kiểm tra tiền tố mã QR hợp lệ hệ thống
        if (qrContent == null || !qrContent.startsWith("AK-CINEMA-ORDER-")) {
            return ResponseEntity.badRequest().body(ApiResponse.<OrderResponse>builder()
                    .status(400)
                    .message("Mã QR không hợp lệ hoặc không thuộc hệ thống A&K Cinema!")
                    .build());
        }

        try {
            // 2. Cắt chuỗi lấy chính xác mã ID đơn hàng
            String orderIdStr = qrContent.replace("AK-CINEMA-ORDER-", "").trim();
            Long orderId = Long.parseLong(orderIdStr);

            // 3. 🔥 ĐÃ THAY ĐỔI: Gọi hàm scanOrderTicket mới chứa 4 tầng ràng buộc bảo mật nâng cao
            OrderResponse orderResponse = orderService.scanOrderTicket(orderId);

            // 4. Trả dữ liệu về nếu hợp lệ
            return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                    .status(200)
                    .message("Mã QR hợp lệ!")
                    .data(orderResponse)
                    .build());

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<OrderResponse>builder()
                    .status(400)
                    .message("Định dạng mã ID hóa đơn trong QR bị lỗi chữ/số!")
                    .build());
        } catch (RuntimeException e) {
            // 🔥 ĐẮT GIÁ: Bắt các ngoại lệ Runtime từ Service ném ra (Sai chi nhánh, Đã dùng, SuperAdmin...) để hiển thị thông báo chuẩn lên Frontend
            return ResponseEntity.badRequest().body(ApiResponse.<OrderResponse>builder()
                    .status(400)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.<OrderResponse>builder()
                    .status(500)
                    .message("Lỗi hệ thống khi truy xuất đơn hàng: " + e.getMessage())
                    .build());
        }
    }

    // ================= 🔥 ENDPOINT 2: XÁC NHẬN SỬ DỤNG VÉ (ĐỔI SANG TRẠNG THÁI USED) =================
    @PutMapping("/{id}/confirm-checkin")
    // @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") // Tạm khóa để test local mượt mà
    public ResponseEntity<ApiResponse<OrderResponse>> confirmCheckIn(@PathVariable Long id) {
        try {
            // Gọi hàm confirmCheckIn xử lý chuyển trạng thái đồng bộ Order + Ticket sang USED
            OrderResponse updatedOrder = orderService.confirmCheckIn(id);
            
            return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                    .status(200)
                    .message("Đã hoàn tất thủ tục bàn giao vé cứng & bắp nước thành công!")
                    .data(updatedOrder)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<OrderResponse>builder()
                    .status(400)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.<OrderResponse>builder()
                    .status(500)
                    .message("Lỗi hệ thống khi xác nhận check-in đơn hàng: " + e.getMessage())
                    .build());
        }
    }
}