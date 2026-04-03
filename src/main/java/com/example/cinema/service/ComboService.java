package com.example.cinema.service;

import com.example.cinema.dto.ComboRequest;
import com.example.cinema.entity.Combo;
import java.util.List;

public interface ComboService {
    List<Combo> getAllCombos();
    Combo getComboById(Long id);
    Combo createCombo(ComboRequest request);
    Combo updateCombo(Long id, ComboRequest request);
    void deleteCombo(Long id);
}