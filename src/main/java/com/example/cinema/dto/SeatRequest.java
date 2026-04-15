package com.example.cinema.dto;

import lombok.Data;

@Data
public class SeatRequest {
    private String seatRow;
    private String seatNumber;
    private String seatType; 
    private Double price;
    private Long roomId;
    private String status; 
}