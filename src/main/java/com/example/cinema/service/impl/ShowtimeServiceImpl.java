package com.example.cinema.service.impl;

import com.example.cinema.dto.ShowtimeRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.ShowtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowtimeServiceImpl implements ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final RoomRepository roomRepository;

    @Override
    public List<Showtime> getAll() { return showtimeRepository.findAll(); }

    @Override
    public Showtime getById(Long id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));
    }

    @Override
    public List<Showtime> getByMovie(Long movieId) { return showtimeRepository.findByMovieId(movieId); }

    @Override
    public List<Showtime> getByCinemaItem(Long cinemaItemId) { return showtimeRepository.findByCinemaItemId(cinemaItemId); }

    @Override
    @Transactional
    public Showtime createShowtime(ShowtimeRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Phim không tồn tại"));
        CinemaItem cinemaItem = cinemaItemRepository.findById(request.getCinemaItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh rạp không tồn tại"));
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        LocalDateTime endTime = request.getStartTime().plusMinutes(movie.getDuration() + 15);

        // Kiểm tra trùng lịch tại phòng này
        checkShowtimeOverlap(room.getId(), request.getStartTime(), endTime, null);

        Showtime showtime = new Showtime();
        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setMovie(movie);
        showtime.setCinemaItem(cinemaItem);
        showtime.setRoom(room);

        return showtimeRepository.save(showtime);
    }

    @Override
    @Transactional
    public Showtime updateShowtime(Long id, ShowtimeRequest request) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));
        
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Phim không tồn tại"));
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));
        CinemaItem cinemaItem = cinemaItemRepository.findById(request.getCinemaItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh không tồn tại"));

        LocalDateTime endTime = request.getStartTime().plusMinutes(movie.getDuration() + 15);
        
        // Kiểm tra trùng lịch, loại trừ suất chiếu đang sửa (id)
        checkShowtimeOverlap(room.getId(), request.getStartTime(), endTime, id);

        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setCinemaItem(cinemaItem);
        
        return showtimeRepository.save(showtime);
    }

    @Override
    public List<Showtime> getByMovieAndDate(Long movieId, String dateStr) {
        LocalDate date = LocalDate.parse(dateStr); 
        return showtimeRepository.findByMovieIdAndDate(movieId, date);
    }

    @Override
    @Transactional
    public void deleteShowtime(Long id) {
        if(!showtimeRepository.existsById(id)) throw new ResourceNotFoundException("Không tìm thấy để xóa");
        showtimeRepository.deleteById(id);
    }

    // Gộp 2 hàm check overlap thành 1 cho gọn
    private void checkShowtimeOverlap(Long roomId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        // Tối ưu: Chỉ lấy lịch chiếu của đúng phòng đó để kiểm tra
        List<Showtime> existing = showtimeRepository.findByRoomId(roomId); 
        for (Showtime s : existing) {
            // Nếu đang update, bỏ qua chính nó
            if (excludeId != null && s.getId().equals(excludeId)) continue;

            if (start.isBefore(s.getEndTime()) && end.isAfter(s.getStartTime())) {
                throw new RuntimeException("Lịch chiếu bị trùng! Phòng này đang có phim chiếu từ " 
                        + s.getStartTime().toLocalTime() + " đến " + s.getEndTime().toLocalTime());
            }
        }
    }
}