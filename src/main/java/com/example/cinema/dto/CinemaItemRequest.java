package com.example.cinema.dto;

import lombok.Data;

@Data
public class CinemaItemRequest {
    private String name;
    private String address;
    private String city;
    private Integer hoursPerRoom;
    private Long cinemaId; // Liên kết tới cụm rạp chính
}