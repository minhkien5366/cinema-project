package com.example.cinema.service;

import com.example.cinema.dto.MovieDTO;
import com.example.cinema.dto.MovieRequest;
import com.example.cinema.dto.TopMovieTicketDTO;
import com.example.cinema.entity.Movie;

import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.List;

public interface MovieService {
    Page<MovieDTO> getMovies(String search, String status, int page, int size);
    Movie getMovieDetail(Long id);
    Movie createMovie(MovieRequest request, MultipartFile file);
    Movie updateMovie(Long id, MovieRequest request, MultipartFile file);
    void deleteMovie(Long id);
    Map<String, Object> importExcel(MultipartFile file);    
    // 🎯 THÊM MỚI: Lấy danh sách Top 3 phim bán chạy nhất
    List<TopMovieTicketDTO> getTop3MoviesByTickets();
}