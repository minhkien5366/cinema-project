package com.example.cinema.service;

import com.example.cinema.dto.MovieDTO;
import com.example.cinema.dto.MovieRequest;
import com.example.cinema.entity.Movie;
import org.springframework.data.domain.Page;

public interface MovieService {
    // Lấy danh sách phim có phân trang, tìm kiếm và lọc
    Page<MovieDTO> getMovies(String search, String status, int page, int size);
    
    // Lấy chi tiết một bộ phim
    Movie getMovieDetail(Long id);
    // Thêm mới
    Movie createMovie(MovieRequest request);
    
    // Cập nhật
    Movie updateMovie(Long id, MovieRequest request);
    
    // Xóa
    void deleteMovie(Long id);
}