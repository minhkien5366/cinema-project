package com.example.cinema.service.impl;

import com.example.cinema.dto.MovieDTO;
import com.example.cinema.dto.MovieRequest;
import com.example.cinema.entity.Genre;
import com.example.cinema.entity.Movie;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.GenreRepository;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.service.CloudinaryService; // Import interface mới
import com.example.cinema.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final CloudinaryService cloudinaryService; // 1. Inject CloudinaryService

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

        // 2. Xử lý lưu file ảnh lên Cloudinary
        if (file != null && !file.isEmpty()) {
            try {
                // Lưu vào folder "movies" trên Cloudinary
                String url = cloudinaryService.uploadImage(file, "movies");
                movie.setPosterUrl(url);
            } catch (IOException e) {
                throw new RuntimeException("Lỗi upload ảnh lên Cloudinary: " + e.getMessage());
            }
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

        // 3. Nếu có file mới thì xóa ảnh cũ trên Cloud và lưu ảnh mới
        if (file != null && !file.isEmpty()) {
            try {
                // Xóa ảnh cũ (nếu posterUrl là link cloudinary)
                if (movie.getPosterUrl() != null && movie.getPosterUrl().contains("cloudinary")) {
                    cloudinaryService.deleteImage(movie.getPosterUrl());
                }
                
                // Upload ảnh mới
                String url = cloudinaryService.uploadImage(file, "movies");
                movie.setPosterUrl(url);
            } catch (IOException e) {
                throw new RuntimeException("Lỗi xử lý ảnh Cloudinary: " + e.getMessage());
            }
        }

        return movieRepository.save(movie);
    }

    @Override
    @Transactional
    public void deleteMovie(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim để xóa"));
        
        // 4. Xóa ảnh trên Cloudinary trước khi xóa phim
        if (movie.getPosterUrl() != null && movie.getPosterUrl().contains("cloudinary")) {
            try {
                cloudinaryService.deleteImage(movie.getPosterUrl());
            } catch (IOException e) {
                System.err.println("Lỗi xóa ảnh trên Cloud: " + e.getMessage());
            }
        }
        
        movieRepository.delete(movie);
    }

    // --- HELPER METHODS ---
    // Loại bỏ hàm saveFile và deleteOldFile cũ sử dụng ổ đĩa

    private MovieDTO convertToDTO(Movie movie) {
        MovieDTO dto = new MovieDTO();
        dto.setId(movie.getId());
        dto.setTitle(movie.getTitle());
        // Bây giờ posterUrl đã là link https://res.cloudinary... 
        // Frontend chỉ việc bỏ vào thẻ <img src={...} />
        dto.setPosterUrl(movie.getPosterUrl()); 
        dto.setDuration(movie.getDuration());
        dto.setStatus(movie.getStatus());
        if (movie.getGenre() != null) {
            dto.setGenreName(movie.getGenre().getName());
        }
        return dto;
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
}