package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.VoucherRequest;
import com.example.cinema.entity.User;
import com.example.cinema.entity.Voucher;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final UserRepository userRepository;

    // --- DÀNH CHO CLIENT ---

    /**
     * 1. LẤY MÃ THEO SỰ KIỆN (Promotion)
     * Khi User vào xem một bài viết Sự kiện A, nó sẽ hiện ra list mã 1, 2, 3 gắn với nó.
     */
    @GetMapping("/promotion/{promotionId}")
    public ResponseEntity<ApiResponse<List<Voucher>>> getByPromotion(@PathVariable Long promotionId) {
        return ResponseEntity.ok(ApiResponse.<List<Voucher>>builder()
                .status(200)
                .message("Lấy danh sách mã của sự kiện thành công")
                // Gọi Service xử lý lọc theo promotionId
                .data(voucherService.getAvailableVouchers(promotionId)) 
                .build());
    }

    /**
     * 2. LƯU MÃ VÀO KHO
     */
    @PostMapping("/save/{voucherId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> saveVoucher(
            @PathVariable Long voucherId, 
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // Tìm User thật để lấy ID xịn từ email trong Token
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Lỗi xác thực người dùng"));

        // Truyền userId và voucherId xuống service như cũ
        voucherService.saveVoucherToUser(user.getUserId(), voucherId);
        
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message("Đã lưu mã vào kho của bặn thành công! 🎟️")
                .build());
    }

    /**
     * 3. XEM KHO VOUCHER CÁ NHÂN
     */
    @GetMapping("/my-vouchers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Voucher>>> getMyVouchers(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Lỗi xác thực"));

        return ResponseEntity.ok(ApiResponse.<List<Voucher>>builder()
                .status(200)
                .message("Thành công")
                .data(voucherService.getVouchersByUser(user.getUserId()))
                .build());
    }

    // --- DÀNH CHO QUẢN TRỊ (ADMIN) ---

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Voucher>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<Voucher>>builder()
                .status(200).message("Thành công").data(voucherService.getAllVouchers()).build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Voucher>> create(@RequestBody VoucherRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.<Voucher>builder()
                .status(201).message("Tạo mã thành công").data(voucherService.createVoucher(request)).build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Voucher>> update(@PathVariable Long id, @RequestBody VoucherRequest request) {
        // Implementation for updating voucher
        return ResponseEntity.ok(ApiResponse.<Voucher>builder()
                .status(200).message("Cập nhật mã thành công").data(voucherService.updateVoucher(id, request)).build());
    }


    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200).message("Xóa mã thành công").build());
    }
}