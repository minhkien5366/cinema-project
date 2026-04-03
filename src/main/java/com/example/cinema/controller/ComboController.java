package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.ComboRequest;
import com.example.cinema.entity.Combo;
import com.example.cinema.service.ComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/combos")
@RequiredArgsConstructor
public class ComboController {

    private final ComboService comboService;

    // API Công khai: Khách hàng xem danh sách bắp nước để chọn
    @GetMapping
    public ResponseEntity<ApiResponse<List<Combo>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<Combo>>builder()
                .status(200)
                .message("Lấy danh sách combo thành công")
                .data(comboService.getAllCombos())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Combo>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<Combo>builder()
                .status(200)
                .message("Thành công")
                .data(comboService.getComboById(id))
                .build());
    }

    // API ADMIN: Quản lý Menu bắp nước
    @PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Combo>> create(@RequestBody ComboRequest request) {
        return ResponseEntity.ok(ApiResponse.<Combo>builder()
                .status(201)
                .message("Thêm combo thành công")
                .data(comboService.createCombo(request))
                .build());
    }

    @PutMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Combo>> update(@PathVariable Long id, @RequestBody ComboRequest request) {
        return ResponseEntity.ok(ApiResponse.<Combo>builder()
                .status(200)
                .message("Cập nhật thành công")
                .data(comboService.updateCombo(id, request))
                .build());
    }

    @DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        comboService.deleteCombo(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message("Xóa combo thành công")
                .build());
    }
}