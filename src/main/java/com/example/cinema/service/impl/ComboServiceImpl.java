package com.example.cinema.service.impl;

import com.example.cinema.dto.ComboRequest;
import com.example.cinema.entity.Combo;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.ComboRepository;
import com.example.cinema.service.ComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ComboServiceImpl implements ComboService {

    private final ComboRepository comboRepository;

    @Override
    public List<Combo> getAllCombos() {
        return comboRepository.findAll();
    }

    @Override
    public Combo getComboById(Long id) {
        return comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo không tồn tại với ID: " + id));
    }

    @Override
    @Transactional
    public Combo createCombo(ComboRequest request) {
        Combo combo = new Combo();
        mapRequestToEntity(request, combo);
        return comboRepository.save(combo);
    }

    @Override
    @Transactional
    public Combo updateCombo(Long id, ComboRequest request) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo không tồn tại"));
        
        mapRequestToEntity(request, combo);
        return comboRepository.save(combo);
    }

    @Override
    @Transactional
    public void deleteCombo(Long id) {
        if (!comboRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy combo để xóa");
        }
        comboRepository.deleteById(id);
    }

    private void mapRequestToEntity(ComboRequest request, Combo combo) {
        combo.setName(request.getName());
        combo.setDescription(request.getDescription());
        combo.setImageUrl(request.getImageUrl());
        combo.setPrice(request.getPrice());
    }
}