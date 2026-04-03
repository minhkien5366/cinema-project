package com.example.cinema.dto;

import lombok.Data;

@Data
public class SeatRequest {
    private String seatRow;    // Ví dụ: A, B, C
    private String seatNumber; // Ví dụ: 01, 02, 03
    private String seatType;   // Ví dụ: NORMAL, VIP, COUPLE
    private Double price;
    private Long roomId;       // ID của phòng chứa ghế này
}