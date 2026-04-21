package com.example.cinema.dto;

import lombok.Data;

@Data
public class ReviewRequest {
    private Long movieId;
    private Integer rating; // 1 -> 5 sao
    private String comment;
}