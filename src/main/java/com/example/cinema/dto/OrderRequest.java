package com.example.cinema.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    private List<Long> seatIds; // Danh sách ID ghế
    private List<ComboOrderDTO> combos; // Danh sách combo bắp nước

    // Lớp này chỉ dùng để hứng dữ liệu Postman, KHÔNG PHẢI LÀ BẢNG TRONG DB
    @Data
    public static class ComboOrderDTO {
        private Long comboId;
        private Integer quantity;
    }
}