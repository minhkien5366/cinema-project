package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.PromotionRequest;
import com.example.cinema.entity.Promotion;
import com.example.cinema.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    // Lấy tin tức cho khách hàng (theo rạp)
    @GetMapping("/client/{cinemaItemId}")
    public ResponseEntity<ApiResponse<List<Promotion>>> getForClient(@PathVariable Long cinemaItemId) {
        return ResponseEntity.ok(ApiResponse.<List<Promotion>>builder()
                .status(200).message("Thành công").data(promotionService.getPromotionsForClient(cinemaItemId)).build());
    }

    // Lấy tất cả tin tức (Admin)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Promotion>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<Promotion>>builder()
                .status(200).message("Thành công").data(promotionService.getAllPromotions()).build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Promotion>> create(@RequestBody PromotionRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.<Promotion>builder()
                .status(201).message("Tạo sự kiện thành công").data(promotionService.createPromotion(request)).build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Promotion>> update(@PathVariable Long id, @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.<Promotion>builder()
                .status(200).message("Cập nhật thành công").data(promotionService.updatePromotion(id, request)).build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200).message("Xóa sự kiện thành công").build());
    }
}