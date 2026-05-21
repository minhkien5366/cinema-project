package com.example.cinema.dto;

import lombok.Data;

@Data
public class MovieDTO {

    private Long id;
    private String title;
    private String posterUrl;
    private Integer duration;
    private String genreName;
    private String status;
    private Double rating;

    // 🎯 ĐỘ TUỔI
    private String ageRating;

    // ⭐ TỔNG LƯỢT ĐÁNH GIÁ
    private Long reviewCount;
}