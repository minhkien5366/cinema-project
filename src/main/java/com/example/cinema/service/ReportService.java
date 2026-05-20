package com.example.cinema.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.example.cinema.dto.MovieRatingDTO;
public interface ReportService {
    ByteArrayInputStream exportRevenueReport(Long cinemaId, LocalDateTime start, LocalDateTime end) throws IOException;
    List<Map<String, Object>> getCinemaRankingData(LocalDateTime start, LocalDateTime end);
    List<MovieRatingDTO> getMovieStatistics();
}