package com.example.cinema.repository;

import com.example.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
    List<Showtime> findByMovieId(Long movieId);
    List<Showtime> findByCinemaItem_Id(Long cinemaItemId);
    List<Showtime> findByRoomId(Long roomId);
    // 🔥 THÊM DÒNG NÀY: Kiểm tra xem phòng chiếu (Room ID) đã dính bất kỳ suất chiếu nào chưa
    boolean existsByRoom_Id(Long roomId);

    // Kiểm tra trùng lịch kèm buffer 20 phút dọn phòng
    @Query("SELECT s FROM Showtime s WHERE s.room.id = :roomId " +
           "AND (:newStart < s.endTime OR :newEnd > s.startTime) " +
           "AND NOT (s.endTime <= :startWithBuffer OR s.startTime >= :endWithBuffer)")
    List<Showtime> findOverlappingShowtimes(
            @Param("roomId") Long roomId,
            @Param("newStart") LocalDateTime newStart,
            @Param("newEnd") LocalDateTime newEnd,
            @Param("startWithBuffer") LocalDateTime startWithBuffer,
            @Param("endWithBuffer") LocalDateTime endWithBuffer);

    @Query("SELECT s FROM Showtime s WHERE s.movie.id = :movieId " +
           "AND CAST(s.startTime AS date) = :date")
    List<Showtime> findByMovieIdAndDate(@Param("movieId") Long movieId, @Param("date") LocalDate date);
boolean existsByRoomIdAndEndTimeAfter(
        Long roomId,
        LocalDateTime time
);
boolean existsByRoomId(Long roomId);

@Query("""
SELECT COUNT(s)
FROM Showtime s
WHERE DATE(s.startTime) = CURRENT_DATE
""")
Long countTodayShowtimes();
@Query("""
SELECT DATE(o.createdAt), SUM(o.totalAmount)
FROM Order o
WHERE o.status = 'PAID'
AND o.createdAt >= :start
GROUP BY DATE(o.createdAt)
ORDER BY DATE(o.createdAt)
""")
List<Object[]> revenue7Days(LocalDateTime start);

}