package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.VoucherRequest;
import com.example.cinema.entity.Voucher;
import com.example.cinema.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    // Lấy mã khả dụng cho khách hàng tại một rạp cụ thể
    @GetMapping("/available/{cinemaItemId}")
    public ResponseEntity<ApiResponse<List<Voucher>>> getAvailable(@PathVariable Long cinemaItemId) {
        return ResponseEntity.ok(ApiResponse.<List<Voucher>>builder()
                .status(200).message("Thành công").data(voucherService.getAvailableVouchers(cinemaItemId)).build());
    }

    // Quản lý mã (Admin)
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200).message("Xóa mã thành công").build());
    }
}