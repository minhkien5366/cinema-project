package com.example.cinema.service;

import com.example.cinema.dto.ReviewRequest;
import com.example.cinema.entity.Review;
import java.util.List;

public interface ReviewService {
    
    /**
     * Tạo đánh giá mới kèm các ràng buộc:
     * 1. Phải đăng nhập
     * 2. Phải mua vé (PAID)
     * 3. Phải xem xong phim (EndTime < Now)
     * 4. Thang điểm 1-5, nội dung > 10 ký tự
     */
    Review createReview(ReviewRequest request);

    /**
     * Lấy danh sách đánh giá của một bộ phim (Mới nhất lên đầu)
     */
    List<Review> getReviewsByMovie(Long movieId);

    /**
     * --- FIX LỖI @Override ---
     * Xóa đánh giá (Dành cho Admin hoặc chính chủ)
     */
    void deleteReview(Long reviewId);
}