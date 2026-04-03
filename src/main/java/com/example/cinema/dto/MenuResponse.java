package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MenuResponse {
    private Long id;
    private String name;
    private String slug;
    private String url;
    private String position;
    private Integer sortOrder;
    private String status;
    private List<MenuResponse> children; // Chứa danh sách menu con
}