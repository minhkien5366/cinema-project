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

    // Lấy suất chiếu (Tự động lọc theo quyền Admin chi nhánh)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Showtime>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<Showtime>>builder()
                .status(200).message("Thành công").data(showtimeService.getAll()).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Showtime>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<Showtime>builder()
                .status(200).message("Thành công").data(showtimeService.getById(id)).build());
    }

    @GetMapping("/movie/{movieId}")
    public ResponseEntity<ApiResponse<List<Showtime>>> getByMovie(
            @PathVariable Long movieId,
            @RequestParam(value = "date", required = false) String date) {
        
        List<Showtime> data = (date != null && !date.isEmpty()) 
                ? showtimeService.getByMovieAndDate(movieId, date) 
                : showtimeService.getByMovie(movieId);

        return ResponseEntity.ok(ApiResponse.<List<Showtime>>builder()
                .status(200).message("Thành công").data(data).build());
    }

    @GetMapping("/cinema-item/{cinemaItemId}")
    public ResponseEntity<ApiResponse<List<Showtime>>> getByCinema(@PathVariable Long cinemaItemId) {
        return ResponseEntity.ok(ApiResponse.<List<Showtime>>builder()
                .status(200).message("Thành công").data(showtimeService.getByCinemaItem(cinemaItemId)).build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Showtime>> create(@RequestBody ShowtimeRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.<Showtime>builder()
                .status(201).message("Đã tạo suất chiếu").data(showtimeService.createShowtime(request)).build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Showtime>> update(@PathVariable Long id, @RequestBody ShowtimeRequest request) {
        return ResponseEntity.ok(ApiResponse.<Showtime>builder()
                .status(200).message("Cập nhật thành công").data(showtimeService.updateShowtime(id, request)).build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        showtimeService.deleteShowtime(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200).message("Xóa thành công").build());
    }
}