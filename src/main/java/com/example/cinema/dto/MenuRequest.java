package com.example.cinema.dto;

import lombok.Data;

@Data
public class MenuRequest {
    private String name;
    private String slug;
    private String url;
    private Long parentId; // ID của menu cha (có thể null nếu là menu gốc)
    private String position; // VD: HEADER, FOOTER
    private Integer sortOrder;
    private String status;
}