package com.example.cinema.service.impl;

import com.example.cinema.dto.ComboAdminResponse;
import com.example.cinema.dto.ComboClientResponse; // 🔥 Tiêm DTO mới của khách hàng vào đây
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.CinemaComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CinemaComboServiceImpl implements CinemaComboService {

    private final CinemaComboRepository cinemaComboRepository;
    private final ComboRepository comboRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final UserRepository userRepository;

    // ================= GET ADMIN COMBOS =================

    @Override
    public List<ComboAdminResponse> getCombosForAdmin() {

        User user = getCurrentUser();
        Long cinemaItemId = user.getManagedCinemaItemId();

        List<Combo> allCombos = comboRepository.findAll();

        List<CinemaCombo> configs =
                cinemaComboRepository.findByCinemaItemId(cinemaItemId);

        return allCombos.stream().map(combo -> {

            CinemaCombo config = configs.stream()
                    .filter(c -> c.getCombo().getId().equals(combo.getId()))
                    .findFirst()
                    .orElse(null);

            boolean available =
                    config == null || config.isActive();

            return ComboAdminResponse.builder()
                    .id(combo.getId())
                    .name(combo.getName())
                    .description(combo.getDescription())
                    .imageUrl(combo.getImageUrl())
                    .price(combo.getPrice())
                    .isAvailable(available)
                    .stock(config != null ? config.getStock() : null) // 🔥 ĐÃ THÊM: Trả stock về cho trang quản trị Admin xem luôn
                    .build();

        }).collect(Collectors.toList());
    }

    // ================= TOGGLE =================

    @Override
    @Transactional
    public Boolean toggleCombo(Long comboId) {

        User user = getCurrentUser();
        Long cinemaItemId = user.getManagedCinemaItemId();

        CinemaCombo cinemaCombo =
                cinemaComboRepository
                        .findByCinemaItemIdAndComboId(cinemaItemId, comboId)
                        .orElse(null);

        if (cinemaCombo == null) {

            CinemaItem cinemaItem =
                    cinemaItemRepository.findById(cinemaItemId)
                            .orElseThrow();

            Combo combo =
                    comboRepository.findById(comboId)
                            .orElseThrow();

            cinemaCombo = new CinemaCombo();
            cinemaCombo.setCinemaItem(cinemaItem);
            cinemaCombo.setCombo(combo);
            cinemaCombo.setActive(false);

        } else {
            cinemaCombo.setActive(!cinemaCombo.isActive());
        }

        cinemaComboRepository.save(cinemaCombo);

        return cinemaCombo.isActive(); 
    }

    // ================= UPDATE STOCK FOR ADMIN (HÀM MỚI TINH) =================

    @Override
    @Transactional
    public void updateComboStock(Long comboId, Integer stock) {
        // 1. Xác định Admin chi nhánh đang thao tác thuộc rạp nào
        User user = getCurrentUser();
        Long cinemaItemId = user.getManagedCinemaItemId();

        if (cinemaItemId == null) {
            throw new RuntimeException("Tài khoản của ông chưa được gán quyền quản lý chi nhánh nào!");
        }

        // 2. Chặn lỗi logic nếu nhập số âm
        if (stock != null && stock < 0) {
            throw new RuntimeException("Số lượng tồn kho không được nhỏ hơn 0!");
        }

        // 3. Tìm cấu hình bắp nước của chi nhánh dưới Database
        CinemaCombo cinemaCombo = cinemaComboRepository
                .findByCinemaItemIdAndComboId(cinemaItemId, comboId)
                .orElse(null);

        if (cinemaCombo == null) {
            // Phòng hờ dữ liệu cũ chưa được map tự động, hệ thống tự động sinh liên kết trung gian
            CinemaItem cinemaItem = cinemaItemRepository.findById(cinemaItemId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh rạp"));
            Combo combo = comboRepository.findById(comboId)
                    .orElseThrow(() -> new ResourceNotFoundException("Combo không tồn tại"));

            cinemaCombo = new CinemaCombo();
            cinemaCombo.setCinemaItem(cinemaItem);
            cinemaCombo.setCombo(combo);
            cinemaCombo.setActive(true);
        }

        // 4. Lưu số lượng hàng mới cập nhật xuống DB
        cinemaCombo.setStock(stock);
        cinemaComboRepository.save(cinemaCombo);
    }

    // ================= CUSTOMER (CẬP NHẬT CHUẨN ĐÉT KÈM STOCK) =================

    @Override
    public List<ComboClientResponse> getActiveCombosForCinema(Long cinemaItemId) {
        // 1. Lấy toàn bộ danh mục combo tổng
        List<Combo> allCombos = comboRepository.findAll();

        // 2. Lấy toàn bộ cấu hình thực tế của chi nhánh rạp này
        List<CinemaCombo> configs = cinemaComboRepository.findByCinemaItemId(cinemaItemId);

        // 3. Quy quét so khớp để bóc tách trạng thái hoạt động và số lượng tồn kho
        return allCombos.stream()
                .map(combo -> {
                    CinemaCombo config = configs.stream()
                            .filter(c -> c.getCombo().getId().equals(combo.getId()))
                            .findFirst()
                            .orElse(null);

                    // Nếu config chưa tồn tại (data cũ) thì mặc định là true, ngược lại lấy trạng thái thật của rạp
                    boolean isActive = config == null || config.isActive();
                    
                    // Lấy số lượng kho thực tế của rạp, nếu config rỗng thì gán null để chặn an toàn
                    Integer stock = config != null ? config.getStock() : null;

                    // Chỉ trả về những combo được bật active = true cho giao diện người dùng đặt vé
                    if (isActive) {
                        return ComboClientResponse.builder()
                                .id(combo.getId())
                                .name(combo.getName())
                                .description(combo.getDescription())
                                .imageUrl(combo.getImageUrl())
                                .price(combo.getPrice())
                                .stock(stock) // 🔥 BẮN STOCK VỀ CHO FRONTEND CHECK LUÔN
                                .build();
                    }
                    return null;
                })
                .filter(Objects::nonNull) // Lọc bỏ những combo bị gán null (active = false)
                .collect(Collectors.toList());
    }

    private User getCurrentUser() {
        String email =
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));
    }
}