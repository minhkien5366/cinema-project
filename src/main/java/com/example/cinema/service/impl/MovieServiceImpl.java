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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DateUtil;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        
        // FIX CHÍ MẠNG: Map điểm số rating tổng từ bảng Movie sang DTO để trả về Frontend
        dto.setRating(movie.getRating()); 

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

    @Override
@Transactional
public void importExcel(MultipartFile file) {

    try (InputStream is = file.getInputStream();
         Workbook workbook = new XSSFWorkbook(is)) {

        Sheet sheet = workbook.getSheetAt(0);

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {

            Row row = sheet.getRow(i);
            if (row == null) continue;

            try {

                String title = readString(row.getCell(0));
                String description = readString(row.getCell(1));
                Integer duration = readInt(row.getCell(2));
                String director = readString(row.getCell(3));
                String cast = readString(row.getCell(4));
                String country = readString(row.getCell(5));
                String status = readString(row.getCell(6));
                LocalDate releaseDate =
                        readDate(row.getCell(7), formatter);
                String genreName = readString(row.getCell(8));
                String posterUrl = readString(row.getCell(9));

                if (title == null || duration == null || genreName == null)
                    throw new RuntimeException("Thiếu dữ liệu bắt buộc");

                // check trùng
                if (movieRepository.existsByTitleIgnoreCase(title))
                    throw new RuntimeException("Phim đã tồn tại");

                Genre genre = genreRepository
                        .findByNameIgnoreCase(genreName)
                        .orElseThrow(() ->
                                new RuntimeException("Không tìm thấy genre: " + genreName));

                Movie movie = new Movie();
                movie.setTitle(title);
                movie.setDescription(description);
                movie.setDuration(duration);
                movie.setDirector(director);
                movie.setCast(cast);
                movie.setCountry(country);
                movie.setStatus(status);
                movie.setReleaseDate(releaseDate);
                movie.setGenre(genre);
                movie.setPosterUrl(posterUrl);

                movieRepository.save(movie);

            } catch (Exception rowError) {
                throw new RuntimeException(
                        "Lỗi dòng " + (i + 1) + ": " + rowError.getMessage()
                );
            }
        }

    } catch (Exception e) {
        throw new RuntimeException("Import movie thất bại: " + e.getMessage());
    }
}
private String readString(Cell cell) {
    if (cell == null) return null;
    cell.setCellType(CellType.STRING);
    return cell.getStringCellValue().trim();
}

private Integer readInt(Cell cell) {
    if (cell == null) return null;
    return (int) cell.getNumericCellValue();
}

private LocalDate readDate(Cell cell, DateTimeFormatter formatter) {
    if (cell == null) return null;

    if (cell.getCellType() == CellType.NUMERIC) {
        return cell.getLocalDateTimeCellValue().toLocalDate();
    }

    return LocalDate.parse(
            cell.getStringCellValue(),
            formatter
    );
}
}