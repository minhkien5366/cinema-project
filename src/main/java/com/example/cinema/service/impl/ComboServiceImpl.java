package com.example.cinema.service.impl;

import com.example.cinema.dto.ComboRequest;
import com.example.cinema.entity.Combo;
import com.example.cinema.entity.CinemaItem;
import com.example.cinema.entity.CinemaCombo;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.ComboRepository;
import com.example.cinema.repository.CinemaItemRepository;
import com.example.cinema.repository.CinemaComboRepository;
import com.example.cinema.service.CloudinaryService;
import com.example.cinema.service.ComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComboServiceImpl implements ComboService {

    private final ComboRepository comboRepository;
    private final CloudinaryService cloudinaryService;
    
    // 🔥 BỔ SUNG CHÍ MẠNG: Tiêm các Repository liên quan để phân phối combo tự động về các rạp
    private final CinemaItemRepository cinemaItemRepository;
    private final CinemaComboRepository cinemaComboRepository;

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

        // 1. Lưu combo tổng vào bảng gốc combos
        Combo savedCombo = comboRepository.save(combo);

        // 2. Lấy tất cả các chi nhánh rạp phim (CinemaItem) đang có trên hệ thống
        List<CinemaItem> allCinemas = cinemaItemRepository.findAll();

        // 3. Tự động map combo mới này sang bảng trung gian cinema_combos cho từng rạp
        List<CinemaCombo> cinemaCombos = allCinemas.stream().map(cinema -> {
            CinemaCombo cc = new CinemaCombo();
            cc.setCinemaItem(cinema);
            cc.setCombo(savedCombo);
            cc.setActive(true); // Mặc định mở bán công khai ở các rạp
            cc.setStock(null);  // 🔥 ĐÚNG YÊU CẦU: Ép cứng null để SuperAdmin không can thiệp, Admin chi nhánh tự nhập kho sau
            return cc;
        }).collect(Collectors.toList());

        // 4. Lưu đồng loạt vào Database
        cinemaComboRepository.saveAll(cinemaCombos);

        return savedCombo;
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

        // 🔥 PHÒNG NGỪA LỖI CONSTRAINT: Trước khi xóa combo gốc, dọn sạch liên kết trung gian ở các chi nhánh
        // Nếu Repo của ông chưa viết custom query, dùng tạm đoạn này để quét sạch tránh dính Foreign Key crash DB
        List<CinemaCombo> linkedCombos = cinemaComboRepository.findAll().stream()
                .filter(cc -> cc.getCombo().getId().equals(id))
                .collect(Collectors.toList());
        if (!linkedCombos.isEmpty()) {
            cinemaComboRepository.deleteAll(linkedCombos);
        }

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