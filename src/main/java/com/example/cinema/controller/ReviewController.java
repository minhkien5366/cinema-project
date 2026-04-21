package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.ReviewRequest;
import com.example.cinema.entity.Review;
import com.example.cinema.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<Review>> createReview(@RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.<Review>builder()
                .status(201)
                .message("Đánh giá thành công, cảm ơn bạn!")
                .data(reviewService.createReview(request))
                .build());
    }

    @GetMapping("/movie/{movieId}")
    public ResponseEntity<ApiResponse<List<Review>>> getByMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.<List<Review>>builder()
                .status(200)
                .data(reviewService.getReviewsByMovie(movieId))
                .build());
    }
}