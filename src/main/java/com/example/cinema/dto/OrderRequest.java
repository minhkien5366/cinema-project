package com.example.cinema.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    private Long showtimeId; // BẮT BUỘC: Thêm dòng này để hết lỗi getShowtimeId()
    private List<Long> seatIds;
    private List<ComboOrderDTO> combos;
    private Double totalAmount;   // Nên có để lưu vết số tiền khách thấy lúc đặt
    private String paymentMethod; // Ví dụ: "CASH", "VNPAY"

    @Data
    public static class ComboOrderDTO {
        private Long comboId;
        private Integer quantity;
    }
}