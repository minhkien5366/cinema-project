package com.example.cinema.service.impl;

import com.example.cinema.dto.ComboRequest;
import com.example.cinema.entity.Combo;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.ComboRepository;
import com.example.cinema.service.CloudinaryService;
import com.example.cinema.service.ComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComboServiceImpl implements ComboService {

    private final ComboRepository comboRepository;
    private final CloudinaryService cloudinaryService;

    // ================= GET =================

    @Override
    public List<Combo> getAllCombos() {
        return comboRepository.findAll();
    }

    @Override
    public Combo getComboById(Long id) {
        return comboRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Combo không tồn tại ID: " + id));
    }

    // ================= CREATE =================

    @Override
    @Transactional
    public Combo createCombo(ComboRequest request, MultipartFile file) {

        Combo combo = new Combo();
        mapRequest(request, combo);

        uploadImageIfExist(combo, file);

        return comboRepository.save(combo);
    }

    // ================= UPDATE =================

    @Override
    @Transactional
    public Combo updateCombo(Long id, ComboRequest request, MultipartFile file) {

        Combo combo = getComboById(id);

        mapRequest(request, combo);

        if (file != null && !file.isEmpty()) {

            deleteOldImage(combo);

            uploadImageIfExist(combo, file);
        }

        return comboRepository.save(combo);
    }

    // ================= DELETE =================

    @Override
    @Transactional
    public void deleteCombo(Long id) {

        Combo combo = getComboById(id);

        deleteOldImage(combo);

        comboRepository.delete(combo);
    }

    // ================= HELPER =================

    private void mapRequest(ComboRequest request, Combo combo) {
        combo.setName(request.getName());
        combo.setDescription(request.getDescription());
        combo.setPrice(request.getPrice());
    }

    private void uploadImageIfExist(Combo combo, MultipartFile file) {
        if (file == null || file.isEmpty()) return;

        try {
            String url = cloudinaryService.uploadImage(file, "combos");
            combo.setImageUrl(url);
        } catch (IOException e) {
            throw new RuntimeException("Upload ảnh lỗi");
        }
    }

    private void deleteOldImage(Combo combo) {
        if (combo.getImageUrl() == null) return;

        try {
            cloudinaryService.deleteImage(combo.getImageUrl());
        } catch (IOException e) {
            System.err.println("Không xoá được ảnh cũ");
        }
    }
}