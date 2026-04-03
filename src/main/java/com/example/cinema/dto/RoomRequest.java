package com.example.cinema.dto;

import lombok.Data;

@Data
public class RoomRequest {
    private String name;
    private Integer totalSeats;
    private Long cinemaItemId; // Liên kết tới chi nhánh rạp cụ thể
}