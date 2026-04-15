package com.example.cinema.service.impl;

import com.example.cinema.dto.MovieDTO;
import com.example.cinema.dto.MovieRequest;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;

    // Đường dẫn lưu file trên máy
    private final String uploadDir = "uploads/movies/";

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
    public Movie createMovie(MovieRequest request, MultipartFile file) {
        Genre genre = genreRepository.findById(request.getGenreId())
                .orElseThrow(() -> new ResourceNotFoundException("Thể loại không tồn tại"));

        Movie movie = new Movie();
        mapRequestToEntity(request, movie, genre);

        // Xử lý lưu file ảnh
        if (file != null && !file.isEmpty()) {
            movie.setPosterUrl(saveFile(file));
        }

        return movieRepository.save(movie);
    }

    @Override
    @Transactional
    public Movie updateMovie(Long id, MovieRequest request, MultipartFile file) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim để cập nhật"));

        Genre genre = genreRepository.findById(request.getGenreId())
                .orElseThrow(() -> new ResourceNotFoundException("Thể loại không tồn tại"));

        mapRequestToEntity(request, movie, genre);

        // Nếu có file mới thì xóa ảnh cũ và lưu ảnh mới
        if (file != null && !file.isEmpty()) {
            deleteOldFile(movie.getPosterUrl());
            movie.setPosterUrl(saveFile(file));
        }

        return movieRepository.save(movie);
    }

    @Override
    @Transactional
    public void deleteMovie(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim để xóa"));
        
        deleteOldFile(movie.getPosterUrl()); // Xóa file ảnh trên ổ đĩa
        movieRepository.delete(movie);
    }

    // --- HELPER METHODS ---

    private String saveFile(MultipartFile file) {
        try {
            Path path = Paths.get(uploadDir);
            if (!Files.exists(path)) Files.createDirectories(path);

            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Files.copy(file.getInputStream(), path.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu file: " + e.getMessage());
        }
    }

    private void deleteOldFile(String fileName) {
        if (fileName != null) {
            try {
                Files.deleteIfExists(Paths.get(uploadDir + fileName));
            } catch (IOException ignored) {}
        }
    }

    private void mapRequestToEntity(MovieRequest request, Movie movie, Genre genre) {
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setDuration(request.getDuration());
        movie.setDirector(request.getDirector());
        movie.setCast(request.getCast());
        movie.setCountry(request.getCountry());
        movie.setStatus(request.getStatus());
        movie.setTrailerUrl(request.getTrailerUrl());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setGenre(genre);
    }

    private MovieDTO convertToDTO(Movie movie) {
        MovieDTO dto = new MovieDTO();
        dto.setId(movie.getId());
        dto.setTitle(movie.getTitle());
        // Trả về full URL để Frontend hiển thị được
        dto.setPosterUrl("/uploads/movies/" + movie.getPosterUrl());
        dto.setDuration(movie.getDuration());
        dto.setStatus(movie.getStatus());
        if (movie.getGenre() != null) {
            dto.setGenreName(movie.getGenre().getName());
        }
        return dto;
    }
}