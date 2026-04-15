package com.example.cinema.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String status;
    private Double totalAmount;
    private String paymentMethod;
    private LocalDateTime createdAt;
    private Long cinemaItemId;
    private String cinemaName;
    private List<OrderDetailResponse> orderDetails;
}