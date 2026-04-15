package com.example.cinema.dto;

import lombok.Data;

@Data
public class PromotionRequest {
    private String title;
    private String content;
    private String image;
    private Long voucherId;    // Có thể đính kèm voucher vào bài viết
    private Long movieId;      // Hoặc đính kèm ưu đãi cho 1 phim cụ thể
    private Long cinemaItemId; // Tin tức của rạp nào (NULL = Tin chung)
}