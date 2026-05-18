package com.example.cinema.controller;

import com.example.cinema.dto.ComboAdminResponse;
import com.example.cinema.dto.ComboClientResponse; 
import com.example.cinema.dto.ApiResponse;
import com.example.cinema.service.CinemaComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/cinema-combos")
@RequiredArgsConstructor
public class CinemaComboController {

    private final CinemaComboService cinemaComboService;

    @GetMapping
    public List<ComboAdminResponse> getCombos() {
        return cinemaComboService.getCombosForAdmin();
    }

    @PatchMapping("/{comboId}/toggle")
    public Boolean toggle(@PathVariable Long comboId) {
        return cinemaComboService.toggleCombo(comboId);
    }

    // 🔥 TÍNH NĂNG MỚI: Endpoint cho phép Admin chi nhánh cập nhật số lượng tồn kho bắp nước
    @PutMapping("/{comboId}/stock")
    public ApiResponse<Void> updateStock(
            @PathVariable Long comboId, 
            @RequestParam Integer stock) {
        
        cinemaComboService.updateComboStock(comboId, stock);
        
        return ApiResponse.<Void>builder()
                .status(200)
                .message("Cập nhật số lượng tồn kho thành công!")
                .build();
    }

    @GetMapping("/{cinemaItemId}/combos")
    public ApiResponse<List<ComboClientResponse>> getActiveCombos(
            @PathVariable Long cinemaItemId) {

        List<ComboClientResponse> combos =
                cinemaComboService.getActiveCombosForCinema(cinemaItemId);

        return ApiResponse.<List<ComboClientResponse>>builder()
                .status(200)
                .message("Lấy combo đang bán thành công")
                .data(combos)
                .build();
    }
}