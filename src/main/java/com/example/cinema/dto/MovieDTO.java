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
    private Double rating; // FIX: Bổ sung trường này để chứa điểm số tổng gửi về Frontend
}