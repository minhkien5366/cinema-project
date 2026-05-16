package com.example.cinema.service.impl;

import com.example.cinema.dto.ReviewRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;

    @Override
    @Transactional
    public Review createReview(ReviewRequest request) {
        User user = getCurrentUser();
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Phim không tồn tại"));

        // 1. RÀNG BUỘC ĐÃ CẬP NHẬT: Thang điểm chấp nhận từ 0.5 đến 5.0 sao
        if (request.getRating() < 0.5 || request.getRating() > 5.0) {
            throw new RuntimeException("Điểm đánh giá phải nằm trong khoảng từ 0.5 đến 5 sao.");
        }

        // 2. RÀNG BUỘC: Nội dung tối thiểu 10 ký tự
        if (request.getComment() == null || request.getComment().trim().length() < 10) {
            throw new RuntimeException("Nội dung đánh giá phải có ít nhất 10 ký tự.");
        }

        // 3. RÀNG BUỘC: Chỉ người đã thanh toán (PAID) mới được đánh giá
        boolean hasPaidTicket = ticketRepository.existsByUser_UserIdAndShowtime_Movie_IdAndStatus(
                user.getUserId(), movie.getId(), "PAID"
        );
        if (!hasPaidTicket) {
            throw new RuntimeException("Bạn cần mua vé để thực hiện đánh giá bộ phim này.");
        }

        // 4. RÀNG BUỘC: Chỉ đánh giá sau khi suất chiếu kết thúc
        boolean hasFinishedWatching = ticketRepository.existsByUser_UserIdAndShowtime_Movie_IdAndStatusAndShowtime_EndTimeBefore(
                user.getUserId(), movie.getId(), "PAID", LocalDateTime.now()
        );
        if (!hasFinishedWatching) {
            throw new RuntimeException("Vui lòng quay lại đánh giá sau khi bộ phim kết thúc.");
        }

        // 5. RÀNG BUỘC: Mỗi người chỉ đánh giá 1 phim 1 lần
        if (reviewRepository.existsByUser_UserIdAndMovieId(user.getUserId(), movie.getId())) {
            throw new RuntimeException("Bạn đã đánh giá bộ phim này rồi.");
        }

        Review review = Review.builder()
                .rating(request.getRating())
                .comment(request.getComment())
                .user(user)
                .movie(movie)
                .createdAt(LocalDateTime.now())
                .build();

        Review savedReview = reviewRepository.save(review);
        updateMovieRatingCache(movie); // Cập nhật lại điểm trung bình
        return savedReview;
    }

    /**
     * Admin hoặc chính chủ nhân của review xóa đánh giá không phù hợp
     */
    @Override
    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá này"));

        User currentUser = getCurrentUser();
        
        boolean isAdmin = currentUser.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals("ADMIN") || r.getRoleName().equals("SUPER_ADMIN"));
        boolean isOwner = review.getUser().getUserId().equals(currentUser.getUserId());

        if (!isAdmin && !isOwner) {
            throw new RuntimeException("Bạn không có quyền xóa đánh giá này!");
        }

        Movie movie = review.getMovie();
        reviewRepository.delete(review);

        // Sau khi xóa, tính lại điểm trung bình cho phim
        updateMovieRatingCache(movie);
    }

    @Override
    public List<Review> getReviewsByMovie(Long movieId) {
        return reviewRepository.findByMovieIdOrderByCreatedAtDesc(movieId);
    }

    /**
     * Hàm dùng chung để tính toán lại Rating cho phim công thức: (Tổng điểm / Tổng lượt Review)
     */
    private void updateMovieRatingCache(Movie movie) {
        Double avgRating = reviewRepository.getAverageRatingByMovieId(movie.getId());
        // Làm tròn đến 1 chữ số thập phân
        double roundedRating = (avgRating != null) ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
        movie.setRating(roundedRating);
        movieRepository.save(movie);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập hết hạn"));
    }
}