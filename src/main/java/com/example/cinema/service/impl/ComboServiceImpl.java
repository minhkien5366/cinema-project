package com.example.cinema.service.impl;

import com.example.cinema.dto.ComboRequest;
import com.example.cinema.entity.Combo;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.ComboRepository;
import com.example.cinema.service.ComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComboServiceImpl implements ComboService {

    private final ComboRepository comboRepository;
    private final String uploadDir = "uploads/combos/";

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
    public Combo createCombo(ComboRequest request, MultipartFile file) {
        Combo combo = new Combo();
        mapRequestToEntity(request, combo);
        
        if (file != null && !file.isEmpty()) {
            combo.setImageUrl(saveFile(file));
        }
        
        return comboRepository.save(combo);
    }

    @Override
    @Transactional
    public Combo updateCombo(Long id, ComboRequest request, MultipartFile file) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo không tồn tại"));
        
        mapRequestToEntity(request, combo);

        if (file != null && !file.isEmpty()) {
            deleteOldFile(combo.getImageUrl()); // Xóa ảnh cũ
            combo.setImageUrl(saveFile(file));  // Lưu ảnh mới
        }
        
        return comboRepository.save(combo);
    }

    @Override
    @Transactional
    public void deleteCombo(Long id) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy combo để xóa"));
        
        deleteOldFile(combo.getImageUrl());
        comboRepository.delete(combo);
    }

    // --- HELPER METHODS ---

    private String saveFile(MultipartFile file) {
        try {
            Path path = Paths.get(uploadDir);
            if (!Files.exists(path)) Files.createDirectories(path);

            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Files.copy(file.getInputStream(), path.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("Lỗi lưu ảnh bắp nước: " + e.getMessage());
        }
    }

    private void deleteOldFile(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            try {
                Files.deleteIfExists(Paths.get(uploadDir + fileName));
            } catch (IOException ignored) {}
        }
    }

    private void mapRequestToEntity(ComboRequest request, Combo combo) {
        combo.setName(request.getName());
        combo.setDescription(request.getDescription());
        combo.setPrice(request.getPrice());
    }
}