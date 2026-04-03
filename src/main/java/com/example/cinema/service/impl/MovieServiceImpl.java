package com.example.cinema.service.impl;

import com.example.cinema.dto.MovieDTO;
import com.example.cinema.dto.MovieRequest; // Cần tạo DTO này
import com.example.cinema.entity.Genre;
import com.example.cinema.entity.Movie;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.GenreRepository;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;

    @Override
    public Page<MovieDTO> getMovies(String search, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Movie> moviePage;

        if (search != null && !search.isEmpty()) {
            moviePage = movieRepository.findByTitleContainingIgnoreCase(search, pageable);
        } else if (status != null && !status.isEmpty()) {
            moviePage = movieRepository.findByStatus(status, pageable);
        } else {
            moviePage = movieRepository.findAll(pageable);
        }

        List<MovieDTO> dtos = moviePage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, moviePage.getTotalElements());
    }

    @Override
    public Movie getMovieDetail(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim với ID: " + id));
    }

    @Override
    @Transactional
    public Movie createMovie(MovieRequest request) {
        Genre genre = genreRepository.findById(request.getGenreId())
                .orElseThrow(() -> new ResourceNotFoundException("Thể loại không tồn tại"));

        Movie movie = new Movie();
        mapRequestToEntity(request, movie, genre);
        return movieRepository.save(movie);
    }

    @Override
    @Transactional
    public Movie updateMovie(Long id, MovieRequest request) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim để cập nhật"));

        Genre genre = genreRepository.findById(request.getGenreId())
                .orElseThrow(() -> new ResourceNotFoundException("Thể loại không tồn tại"));

        mapRequestToEntity(request, movie, genre);
        return movieRepository.save(movie);
    }

    @Override
    @Transactional
    public void deleteMovie(Long id) {
        if (!movieRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy phim để xóa");
        }
        movieRepository.deleteById(id);
    }

    private void mapRequestToEntity(MovieRequest request, Movie movie, Genre genre) {
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setDuration(request.getDuration());
        movie.setDirector(request.getDirector());
        movie.setCast(request.getCast());
        movie.setCountry(request.getCountry());
        movie.setStatus(request.getStatus());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setTrailerUrl(request.getTrailerUrl());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setGenre(genre);
    }

    private MovieDTO convertToDTO(Movie movie) {
        MovieDTO dto = new MovieDTO();
        dto.setId(movie.getId());
        dto.setTitle(movie.getTitle());
        dto.setPosterUrl(movie.getPosterUrl());
        dto.setDuration(movie.getDuration());
        dto.setStatus(movie.getStatus());
        if (movie.getGenre() != null) {
            dto.setGenreName(movie.getGenre().getName());
        }
        return dto;
    }
}