package com.example.cinema.service;

import com.example.cinema.dto.ComboAdminResponse;
import com.example.cinema.entity.Combo;

import java.util.List;

public interface CinemaComboService {

    List<ComboAdminResponse> getCombosForAdmin();

    Boolean toggleCombo(Long comboId);

    List<Combo> getActiveCombosForCinema(Long cinemaItemId);
}