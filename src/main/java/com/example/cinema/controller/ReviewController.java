package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.ReviewRequest;
import com.example.cinema.entity.Review;
import com.example.cinema.service.CloudinaryService;
import com.example.cinema.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final CloudinaryService cloudinaryService;

    // 🎯 THÊM HÀM UPLOAD ẢNH KÈM REVIEW
    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<ApiResponse<Review>> createReview(
            @RequestParam("rating") Double rating,
            @RequestParam("comment") String comment,
            @RequestParam("movieId") Long movieId,
            @RequestParam(value = "image", required = false) MultipartFile image) throws IOException {

        // 1. Nếu có ảnh, upload lên Cloudinary
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            imageUrl = cloudinaryService.uploadImage(image, "reviews");
        }

        // 2. Map dữ liệu vào DTO
        ReviewRequest request = new ReviewRequest();
        request.setRating(rating);
        request.setComment(comment);
        request.setMovieId(movieId);
        request.setImageUrl(imageUrl); // Đảm bảo ReviewRequest có trường imageUrl

        // 3. Lưu vào Database thông qua service
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

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<String>> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message("Đã xóa đánh giá thành công!")
                .build());
    }
}