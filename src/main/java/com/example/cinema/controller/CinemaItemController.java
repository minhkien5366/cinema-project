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

    @GetMapping
    public ResponseEntity<ApiResponse<List<CinemaItem>>> getAll(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long cinemaId) {
        List<CinemaItem> data;
        if (city != null) data = itemService.getByCity(city);
        else if (cinemaId != null) data = itemService.getByCinema(cinemaId);
        else data = itemService.getAllItems();
        
        return ResponseEntity.ok(ApiResponse.<List<CinemaItem>>builder()
                .status(200).message("Thành công").data(data).build());
    }

    @PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CinemaItem>> create(@RequestBody CinemaItemRequest request) {
        return ResponseEntity.ok(ApiResponse.<CinemaItem>builder()
                .status(201).message("Đã tạo chi nhánh").data(itemService.createItem(request)).build());
    }
}