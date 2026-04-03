package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.CinemaRequest;
import com.example.cinema.entity.Cinema;
import com.example.cinema.service.CinemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
public class CinemaController {
    private final CinemaService cinemaService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Cinema>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<Cinema>>builder()
                .status(200).message("Thành công").data(cinemaService.getAllCinemas()).build());
    }

    @PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Cinema>> create(@RequestBody CinemaRequest request) {
        return ResponseEntity.ok(ApiResponse.<Cinema>builder()
                .status(201).message("Đã tạo").data(cinemaService.createCinema(request)).build());
    }
}