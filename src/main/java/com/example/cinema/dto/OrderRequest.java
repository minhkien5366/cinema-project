package com.example.cinema.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    private Long showtimeId;      // ID của suất chiếu
    private List<Long> seatIds;   // Danh sách ID ghế chọn
    private List<ComboOrderDTO> combos; 
    private Double totalAmount;   // Tổng tiền gửi từ FE
    private String paymentMethod; // "MOMO", "VNPAY", hoặc "CASH"

    @Data
    public static class ComboOrderDTO {
        private Long comboId;
        private Integer quantity;
    }
}