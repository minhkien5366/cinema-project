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

        // 1. RÀNG BUỘC: Chỉ được đánh giá khi phim đã chiếu xong (EndTime < Hiện tại)
        boolean hasFinishedWatching = ticketRepository.existsByUser_UserIdAndShowtime_Movie_IdAndStatusAndShowtime_EndTimeBefore(
                user.getUserId(), 
                movie.getId(), 
                "PAID", 
                LocalDateTime.now()
        );

        if (!hasFinishedWatching) {
            throw new RuntimeException("Bạn chỉ có thể đánh giá sau khi bộ phim đã kết thúc suất chiếu!");
        }

        // 2. RÀNG BUỘC: Check xem đã đánh giá chưa (Sử dụng hàm đã fix tên)
        if (reviewRepository.existsByUser_UserIdAndMovieId(user.getUserId(), movie.getId())) {
            throw new RuntimeException("Bạn đã gửi đánh giá cho bộ phim này rồi.");
        }

        // 3. LƯU REVIEW
        Review review = Review.builder()
                .rating(request.getRating())
                .comment(request.getComment())
                .user(user)
                .movie(movie)
                .build();

        Review savedReview = reviewRepository.save(review);

        // 4. CẬP NHẬT CỘT RATING TRONG BẢNG MOVIE
        updateMovieRatingCache(movie);

        return savedReview;
    }

    @Override
    public List<Review> getReviewsByMovie(Long movieId) {
        return reviewRepository.findByMovieIdOrderByCreatedAtDesc(movieId);
    }

    private void updateMovieRatingCache(Movie movie) {
        Double avgRating = reviewRepository.getAverageRatingByMovieId(movie.getId());
        double roundedRating = (avgRating != null) ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
        movie.setRating(roundedRating);
        movieRepository.save(movie);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập hết hạn, vui lòng thử lại"));
    }
}