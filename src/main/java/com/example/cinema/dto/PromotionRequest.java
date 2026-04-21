package com.example.cinema.dto;

import lombok.Data;

@Data
public class PromotionRequest {
    private String title;
    private String content;
    private Long movieId;     
    private Long cinemaItemId; 
}