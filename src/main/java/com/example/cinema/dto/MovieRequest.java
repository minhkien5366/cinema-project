package com.example.cinema.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class MovieRequest {
    private String title;
    private String description;
    private Integer duration;
    private String director;
    private String cast;
    private String country;
    private String status;
    private String trailerUrl;
    private LocalDate releaseDate;
    private Integer genreId; 
    // Không để posterUrl ở đây nữa vì sẽ truyền file riêng qua Controller
}