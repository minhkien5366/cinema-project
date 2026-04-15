package com.example.cinema.service;

import com.example.cinema.dto.MovieDTO;
import com.example.cinema.dto.MovieRequest;
import com.example.cinema.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

public interface MovieService {
    Page<MovieDTO> getMovies(String search, String status, int page, int size);
    Movie getMovieDetail(Long id);
    Movie createMovie(MovieRequest request, MultipartFile file); // Thêm file
    Movie updateMovie(Long id, MovieRequest request, MultipartFile file); // Thêm file
    void deleteMovie(Long id);
}