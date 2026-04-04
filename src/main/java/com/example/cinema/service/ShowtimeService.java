package com.example.cinema.service;

import com.example.cinema.dto.ShowtimeRequest;
import com.example.cinema.entity.Showtime;
import java.util.List;

public interface ShowtimeService {
    List<Showtime> getAll();
    Showtime getById(Long id); // Thêm dòng này
    List<Showtime> getByMovie(Long movieId);
    List<Showtime> getByCinemaItem(Long cinemaItemId);
    Showtime createShowtime(ShowtimeRequest request);
    Showtime updateShowtime(Long id, ShowtimeRequest request);
    void deleteShowtime(Long id);
    List<Showtime> getByMovieAndDate(Long movieId, String dateStr);
}