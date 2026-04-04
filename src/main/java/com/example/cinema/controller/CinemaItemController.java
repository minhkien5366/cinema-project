package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.CinemaItemRequest;
import com.example.cinema.entity.CinemaItem;
import com.example.cinema.service.CinemaItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cinema-items")
@RequiredArgsConstructor
public class CinemaItemController {
    private final CinemaItemService itemService;

    // 1. Lấy danh sách chi nhánh (Hỗ trợ lọc theo thành phố hoặc cụm rạp)
    @GetMapping
    public ResponseEntity<ApiResponse<List<CinemaItem>>> getAll(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long cinemaId) {
        
        List<CinemaItem> data;
        if (city != null) {
            data = itemService.getByCity(city);
        } else if (cinemaId != null) {
            data = itemService.getByCinema(cinemaId);
        } else {
            data = itemService.getAllItems();
        }
        
        return ResponseEntity.ok(ApiResponse.<List<CinemaItem>>builder()
                .status(200)
                .message("Lấy danh sách chi nhánh thành công")
                .data(data)
                .build());
    }

    // 2. Lấy chi tiết một chi nhánh rạp (Đã fix lỗi 501)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CinemaItem>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<CinemaItem>builder()
                .status(200)
                .message("Lấy thông tin chi nhánh thành công")
                .data(itemService.getItemById(id))
                .build());
    }

    // 3. Tạo mới chi nhánh rạp (Chỉ Admin/Super Admin)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CinemaItem>> create(@RequestBody CinemaItemRequest request) {
        CinemaItem newItem = itemService.createItem(request);
        return ResponseEntity.status(201).body(ApiResponse.<CinemaItem>builder()
                .status(201)
                .message("Đã tạo chi nhánh mới thành công")
                .data(newItem)
                .build());
    }

    // 4. Cập nhật chi nhánh (Chỉ Admin/Super Admin)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CinemaItem>> update(@PathVariable Long id, @RequestBody CinemaItemRequest request) {
        CinemaItem updatedItem = itemService.updateItem(id, request);
        return ResponseEntity.ok(ApiResponse.<CinemaItem>builder()
                .status(200)
                .message("Đã cập nhật chi nhánh thành công")
                .data(updatedItem)
                .build());
    }

    // 5. Xóa chi nhánh (Chỉ Admin/Super Admin)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        itemService.deleteItem(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(200)
                .message("Đã xóa chi nhánh thành công")
                .build());
    }
}