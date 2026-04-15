package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.MovieDTO;
import com.example.cinema.dto.MovieRequest;
import com.example.cinema.entity.Movie;
import com.example.cinema.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    // 1. Lấy danh sách phim (Public)
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

    // 2. Lấy chi tiết phim (Public)
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

    // 3. Thêm phim mới kèm Upload ảnh (Admin/Super Admin)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Movie>> createMovie(
            @RequestPart("movie") MovieRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        Movie movie = movieService.createMovie(request, file);
        return ResponseEntity.ok(
                ApiResponse.<Movie>builder()
                        .status(201)
                        .message("Thêm phim và upload ảnh thành công")
                        .data(movie)
                        .build()
        );
    }

    // 4. Cập nhật phim kèm đổi ảnh (Admin/Super Admin)
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Movie>> updateMovie(
            @PathVariable Long id,
            @RequestPart("movie") MovieRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        Movie movie = movieService.updateMovie(id, request, file);
        return ResponseEntity.ok(
                ApiResponse.<Movie>builder()
                        .status(200)
                        .message("Cập nhật phim thành công")
                        .data(movie)
                        .build()
        );
    }

    // 5. Xóa phim (Admin/Super Admin)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteMovie(@PathVariable Long id) {
        movieService.deleteMovie(id);
        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .status(200)
                        .message("Xóa phim và file ảnh thành công")
                        .build()
        );
    }
}