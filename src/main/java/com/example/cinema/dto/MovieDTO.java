package com.example.cinema.dto;

import lombok.Data;

@Data
public class MovieDTO {
    private Long id;
    private String title;
    private String posterUrl;
    private Integer duration;
    private String genreName; // Trả về tên thể loại thay vì ID
    private String status;
}