package com.example.cinema.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    
    @JsonProperty("showtimeId")
    @JsonAlias({"id", "showtime_id", "stId", "infoId"}) // Chấp nhận mọi biến thể tên gọi từ FE
    private Long showtimeId; 
    
    @JsonProperty("seatIds")
    private List<Long> seatIds;
    
    @JsonProperty("combos")
    private List<ComboOrderDTO> combos;
    
    @JsonProperty("totalAmount")
    private Double totalAmount;
    
    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComboOrderDTO {
        @JsonProperty("comboId")
        private Long comboId;
        @JsonProperty("quantity")
        private Integer quantity;
    }
}