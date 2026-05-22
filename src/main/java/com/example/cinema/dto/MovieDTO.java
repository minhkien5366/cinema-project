package com.example.cinema.dto;

import lombok.Data;
import java.util.Set;

@Data
public class MovieDTO {
    private Long id;
    private String title;
    private String posterUrl;
    private Integer duration;
    
    // 🎯 THAY ĐỔI: Chuyển từ String genreName sang Set<String> genreNames
    private Set<String> genreNames;
    
    private String status;
    private Double rating;

    // ĐỘ TUỔI
    private String ageRating;

    // TỔNG LƯỢT ĐÁNH GIÁ
    private Long reviewCount;
}