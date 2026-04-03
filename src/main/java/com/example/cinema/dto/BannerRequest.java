package com.example.cinema.dto;

import lombok.Data;

@Data
public class BannerRequest {
    private String title;
    private String imageUrl;
    private String linkUrl;
    private String position;
    private String status;
    private Integer sortOrder;
}