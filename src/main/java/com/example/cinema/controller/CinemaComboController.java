package com.example.cinema.controller;

import com.example.cinema.dto.ComboAdminResponse;
import com.example.cinema.dto.ApiResponse;
import com.example.cinema.entity.Combo;
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
    @GetMapping("/{cinemaItemId}/combos")
        public ApiResponse<List<Combo>> getActiveCombos(
                @PathVariable Long cinemaItemId) {

            List<Combo> combos =
                    cinemaComboService.getActiveCombosForCinema(cinemaItemId);

            return ApiResponse.<List<Combo>>builder()
                    .status(200)
                    .message("Lấy combo đang bán thành công")
                    .data(combos)
                    .build();
        }
    
}