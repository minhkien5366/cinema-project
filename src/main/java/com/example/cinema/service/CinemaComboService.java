package com.example.cinema.service;

import com.example.cinema.dto.ComboAdminResponse;
import com.example.cinema.dto.ComboClientResponse; // 🔥 Nhớ import DTO mới vào đây
import java.util.List;

public interface CinemaComboService {
    
    List<ComboAdminResponse> getCombosForAdmin();
    
    Boolean toggleCombo(Long comboId);
    
    void updateComboStock(Long comboId, Integer stock);
    // 🔥 SỬA DÒNG NÀY: Đổi từ List<Combo> thành List<ComboClientResponse>
    List<ComboClientResponse> getActiveCombosForCinema(Long cinemaItemId);
}