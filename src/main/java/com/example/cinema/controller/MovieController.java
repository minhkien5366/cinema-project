package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.MovieDTO;
import com.example.cinema.dto.MovieRequest;
import com.example.cinema.entity.Movie;
import com.example.cinema.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<MovieDTO>>> getMovies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<MovieDTO> movies = movieService.getMovies(search, status, page, size);
        return ResponseEntity.ok(
                ApiResponse.<Page<MovieDTO>>builder()
                        .status(200)
                        .message("Lấy danh sách phim thành công")
                        .data(movies)
                        .build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Movie>> getMovieDetail(@PathVariable Long id) {
        Movie movie = movieService.getMovieDetail(id);
        return ResponseEntity.ok(
                ApiResponse.<Movie>builder()
                        .status(200)
                        .message("Lấy chi tiết phim thành công")
                        .data(movie)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") // Chỉ Admin mới được thêm phim
    public ResponseEntity<ApiResponse<Movie>> createMovie(@RequestBody MovieRequest request) {
        Movie movie = movieService.createMovie(request);
        return ResponseEntity.ok(
                ApiResponse.<Movie>builder()
                        .status(201)
                        .message("Thêm phim thành công")
                        .data(movie)
                        .build()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") // Chỉ Admin mới được sửa phim
    public ResponseEntity<ApiResponse<Movie>> updateMovie(@PathVariable Long id, @RequestBody MovieRequest request) {
        Movie movie = movieService.updateMovie(id, request);
        return ResponseEntity.ok(
                ApiResponse.<Movie>builder()
                        .status(200)
                        .message("Cập nhật phim thành công")
                        .data(movie)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") // Chỉ Admin mới được xóa phim
    public ResponseEntity<ApiResponse<String>> deleteMovie(@PathVariable Long id) {
        movieService.deleteMovie(id);
        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .status(200)
                        .message("Xóa phim thành công")
                        .build()
        );
    }
}