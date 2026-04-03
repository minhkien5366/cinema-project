package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.BannerRequest;
import com.example.cinema.entity.Banner;
import com.example.cinema.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    // API Công khai: Lấy danh sách banner đang hoạt động để hiển thị lên Web/App
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<Banner>>> getActiveBanners() {
        return ResponseEntity.ok(ApiResponse.<List<Banner>>builder()
                .status(200)
                .message("Lấy danh sách banner thành công")
                .data(bannerService.getActiveBanners())
                .build());
    }

    // API Admin: Quản lý tất cả banner
    @GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Banner>>> getAllBanners() {
        return ResponseEntity.ok(ApiResponse.<List<Banner>>builder()
                .status(200)
                .message("Lấy tất cả banner thành công")
                .data(bannerService.getAllBanners())
                .build());
    }

    @PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Banner>> createBanner(@RequestBody BannerRequest request) {
        return ResponseEntity.ok(ApiResponse.<Banner>builder()
                .status(201)
                .message("Thêm banner thành công")
                .data(bannerService.createBanner(request))
                .build());
    }

    @PutMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Banner>> updateBanner(@PathVariable Long id, @RequestBody BannerRequest request) {
        return ResponseEntity.ok(ApiResponse.<Banner>builder()
                .status(200)
                .message("Cập nhật banner thành công")
                .data(bannerService.updateBanner(id, request))
                .build());
    }

    @DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteBanner(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message("Xóa banner thành công")
                .build());
    }
}