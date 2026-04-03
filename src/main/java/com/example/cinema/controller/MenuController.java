package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.MenuRequest;
import com.example.cinema.dto.MenuResponse;
import com.example.cinema.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // API Công khai: Lấy danh sách menu để hiển thị trên website
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getMenuTree() {
        return ResponseEntity.ok(ApiResponse.<List<MenuResponse>>builder()
                .status(200)
                .message("Lấy cây menu thành công")
                .data(menuService.getMenuTree())
                .build());
    }

    // API ADMIN/SUPER_ADMIN: Quản lý menu
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<MenuResponse>> createMenu(@RequestBody MenuRequest request) {
        return ResponseEntity.ok(ApiResponse.<MenuResponse>builder()
                .status(201)
                .message("Tạo menu thành công")
                .data(menuService.createMenu(request))
                .build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<MenuResponse>> updateMenu(@PathVariable Long id, @RequestBody MenuRequest request) {
        return ResponseEntity.ok(ApiResponse.<MenuResponse>builder()
                .status(200)
                .message("Cập nhật menu thành công")
                .data(menuService.updateMenu(id, request))
                .build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteMenu(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message("Xóa menu thành công")
                .build());
    }
}