package com.example.cinema.dto;

import lombok.Data;

@Data
public class SeatPriceConfigRequest {
    private String seatType;   // NORMAL, VIP, COUPLE
    private Integer dayOfWeek; // 2 -> 8
    private Double price;
}