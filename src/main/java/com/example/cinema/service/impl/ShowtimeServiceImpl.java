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

        // 1. Tự động tính endTime = startTime + thời lượng phim + 15 phút dọn phòng
        LocalDateTime endTime = request.getStartTime().plusMinutes(movie.getDuration() + 15);

        // 2. Kiểm tra trùng lịch (Overlap check)
        checkShowtimeOverlap(room.getId(), request.getStartTime(), endTime);

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
        
        Movie movie = movieRepository.findById(request.getMovieId()).orElseThrow(() -> new ResourceNotFoundException("Phim không tồn tại"));
        LocalDateTime endTime = request.getStartTime().plusMinutes(movie.getDuration() + 15);
        
        // Kiểm tra trùng lịch nhưng loại trừ chính ID hiện tại
        checkShowtimeOverlapUpdate(request.getRoomId(), request.getStartTime(), endTime, id);

        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setMovie(movie);
        showtime.setRoom(roomRepository.findById(request.getRoomId()).get());
        
        return showtimeRepository.save(showtime);
    }

    @Override
    public List<Showtime> getByMovieAndDate(Long movieId, String dateStr) {
        // Chuyển chuỗi "2026-03-20" thành đối tượng LocalDate
        LocalDate date = LocalDate.parse(dateStr); 
        return showtimeRepository.findByMovieIdAndDate(movieId, date);
    }

    @Override
    @Transactional
    public void deleteShowtime(Long id) {
        showtimeRepository.deleteById(id);
    }

    // Logic kiểm tra trùng lịch chiếu tại cùng 1 phòng
    private void checkShowtimeOverlap(Long roomId, LocalDateTime start, LocalDateTime end) {
        List<Showtime> existing = showtimeRepository.findAll(); // Có thể tối ưu bằng câu Query trong Repository
        for (Showtime s : existing) {
            if (s.getRoom().getId().equals(roomId)) {
                if (start.isBefore(s.getEndTime()) && end.isAfter(s.getStartTime())) {
                    throw new RuntimeException("Phòng này đã có lịch chiếu khác trong khoảng thời gian này!");
                }
            }
        }
    }

    private void checkShowtimeOverlapUpdate(Long roomId, LocalDateTime start, LocalDateTime end, Long id) {
        List<Showtime> existing = showtimeRepository.findAll();
        for (Showtime s : existing) {
            if (!s.getId().equals(id) && s.getRoom().getId().equals(roomId)) {
                if (start.isBefore(s.getEndTime()) && end.isAfter(s.getStartTime())) {
                    throw new RuntimeException("Trùng lịch chiếu!");
                }
            }
        }
    }
}