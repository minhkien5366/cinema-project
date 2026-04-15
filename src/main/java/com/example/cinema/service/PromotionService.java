package com.example.cinema.service;

import com.example.cinema.dto.PromotionRequest;
import com.example.cinema.entity.Promotion;
import java.util.List;

public interface PromotionService {
    // Dành cho khách hàng: Xem tin của rạp đang chọn hoặc tin chung
    List<Promotion> getPromotionsForClient(Long cinemaItemId);

    // Dành cho Admin: Quản lý danh sách tin tức theo quyền
    List<Promotion> getAllPromotions();

    Promotion getPromotionById(Long id);

    Promotion createPromotion(PromotionRequest request);

    Promotion updatePromotion(Long id, PromotionRequest request);

    void deletePromotion(Long id);
}