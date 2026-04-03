package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.SeatRequest;
import com.example.cinema.entity.Seat;
import com.example.cinema.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Seat>>> getAllSeats() {
        return ResponseEntity.ok(ApiResponse.<List<Seat>>builder()
                .status(200)
                .message("Lấy danh sách tất cả ghế thành công")
                .data(seatService.getAllSeats())
                .build());
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<ApiResponse<List<Seat>>> getSeatsByRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(ApiResponse.<List<Seat>>builder()
                .status(200)
                .message("Lấy danh sách ghế của phòng thành công")
                .data(seatService.getSeatsByRoom(roomId))
                .build());
    }

    @PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Seat>> createSeat(@RequestBody SeatRequest request) {
        return ResponseEntity.ok(ApiResponse.<Seat>builder()
                .status(201)
                .message("Tạo ghế thành công")
                .data(seatService.createSeat(request))
                .build());
    }

    // Endpoint mới để sinh ghế hàng loạt
    @PostMapping("/generate")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Seat>>> generateSeats(
            @RequestParam Long roomId,
            @RequestParam int rows,
            @RequestParam int seatsPerRow) {
        return ResponseEntity.ok(ApiResponse.<List<Seat>>builder()
                .status(201)
                .message("Sinh danh sách ghế tự động thành công")
                .data(seatService.generateSeatsForRoom(roomId, rows, seatsPerRow))
                .build());
    }

    @PutMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Seat>> updateSeat(@PathVariable Long id, @RequestBody SeatRequest request) {
        return ResponseEntity.ok(ApiResponse.<Seat>builder()
                .status(200)
                .message("Cập nhật ghế thành công")
                .data(seatService.updateSeat(id, request))
                .build());
    }

    @DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteSeat(@PathVariable Long id) {
        seatService.deleteSeat(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message("Xóa ghế thành công")
                .build());
    }
}