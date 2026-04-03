package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.ShowtimeRequest;
import com.example.cinema.entity.Showtime;
import com.example.cinema.service.ShowtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/showtimes")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    // Lấy lịch chiếu theo phim (Có lọc theo ngày)
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<ApiResponse<List<Showtime>>> getByMovie(
            @PathVariable Long movieId,
            @RequestParam(value = "date", required = false) String date) {
        
        List<Showtime> data;
        if (date != null && !date.isEmpty()) {
            // Gọi hàm lọc theo ngày ông vừa thêm vào Service
            data = showtimeService.getByMovieAndDate(movieId, date);
        } else {
            // Nếu không gửi ngày thì lấy tất cả (giữ nguyên logic cũ)
            data = showtimeService.getByMovie(movieId);
        }

        return ResponseEntity.ok(ApiResponse.<List<Showtime>>builder()
                .status(200)
                .message("Thành công")
                .data(data)
                .build());
    }

    @GetMapping("/cinema-item/{cinemaItemId}")
    public ResponseEntity<ApiResponse<List<Showtime>>> getByCinema(@PathVariable Long cinemaItemId) {
        return ResponseEntity.ok(ApiResponse.<List<Showtime>>builder()
                .status(200).message("Thành công").data(showtimeService.getByCinemaItem(cinemaItemId)).build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Showtime>> create(@RequestBody ShowtimeRequest request) {
        return ResponseEntity.ok(ApiResponse.<Showtime>builder()
                .status(201).message("Đã tạo suất chiếu").data(showtimeService.createShowtime(request)).build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        showtimeService.deleteShowtime(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200).message("Xóa thành công").build());
    }
}