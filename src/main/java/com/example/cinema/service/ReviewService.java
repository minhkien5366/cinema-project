package com.example.cinema.service;

import com.example.cinema.dto.ReviewRequest;
import com.example.cinema.entity.Review;
import java.util.List;

public interface ReviewService {
    // Tạo đánh giá mới (kèm ràng buộc check vé và thời gian)
    Review createReview(ReviewRequest request);

    // Lấy danh sách đánh giá của một bộ phim
    List<Review> getReviewsByMovie(Long movieId);
}