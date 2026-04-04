package com.example.cinema.repository;

import com.example.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    // 1. Tìm suất chiếu theo ID phim (Dùng để hiện lịch chiếu của 1 bộ phim)
    List<Showtime> findByMovieId(Long movieId);

    // 2. Tìm suất chiếu theo chi nhánh rạp
    List<Showtime> findByCinemaItemId(Long cinemaItemId);

    // 3. Tìm suất chiếu theo phòng (BẮT BUỘC có để check trùng lịch - Overlap check)
    List<Showtime> findByRoomId(Long roomId);

    // 4. Lọc theo Phim và chính xác Ngày (Bỏ qua Giờ)
    // Sử dụng CAST để đảm bảo tương thích tốt hơn với nhiều loại Database
    @Query("SELECT s FROM Showtime s WHERE s.movie.id = :movieId " +
           "AND CAST(s.startTime AS date) = :date")
    List<Showtime> findByMovieIdAndDate(@Param("movieId") Long movieId, @Param("date") LocalDate date);

    // 5. (Mở rộng) Tìm các suất chiếu đang diễn ra (dùng cho trang chủ hoặc màn hình rạp)
    @Query("SELECT s FROM Showtime s WHERE s.startTime <= CURRENT_TIMESTAMP AND s.endTime >= CURRENT_TIMESTAMP")
    List<Showtime> findCurrentShowtimes();
}