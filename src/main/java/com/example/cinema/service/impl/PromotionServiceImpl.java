package com.example.cinema.service.impl;

import com.example.cinema.dto.PromotionRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final VoucherRepository voucherRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;

    @Override
    public List<Promotion> getPromotionsForClient(Long cinemaItemId) {
        // Khách hàng xem: Tin của chi nhánh + Tin chung (cinemaItem IS NULL)
        return promotionRepository.findByCinemaItem_IdOrCinemaItemIsNull(cinemaItemId);
    }

    @Override
    public List<Promotion> getAllPromotions() {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) {
            return promotionRepository.findAll();
        }
        // Admin thường: Chỉ thấy tin rạp mình quản lý
        return promotionRepository.findByCinemaItem_Id(user.getManagedCinemaItemId());
    }

    @Override
    public Promotion getPromotionById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện"));
    }

    @Override
    @Transactional
    public Promotion createPromotion(PromotionRequest request) {
        User user = getCurrentUser();
        Promotion promotion = new Promotion();
        mapRequestToEntity(request, promotion);

        // --- FIX LỖI PHÂN QUYỀN TẠI ĐÂY ---
        if (isSuperAdmin(user)) {
            // Super Admin: Có thể tạo tin cho 1 rạp cụ thể hoặc tin chung toàn hệ thống (null)
            if (request.getCinemaItemId() != null) {
                promotion.setCinemaItem(cinemaItemRepository.findById(request.getCinemaItemId()).orElse(null));
            } else {
                promotion.setCinemaItem(null); // Tin tức Global
            }
        } else {
            // Admin thường: Bắt buộc gán vào rạp đang quản lý
            Long managedId = user.getManagedCinemaItemId();
            if (managedId == null) {
                throw new RuntimeException("Lỗi: Tài khoản Admin chưa được gán chi nhánh rạp quản lý!");
            }
            promotion.setCinemaItem(cinemaItemRepository.findById(managedId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh rạp quản lý")));
        }

        return promotionRepository.save(promotion);
    }

    @Override
    @Transactional
    public Promotion updatePromotion(Long id, PromotionRequest request) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sự kiện không tồn tại"));
        
        // Kiểm tra quyền: Super Admin hoặc Admin quản lý đúng rạp đó
        validateAdminPermission(promotion.getCinemaItem() != null ? promotion.getCinemaItem().getId() : null);
        
        mapRequestToEntity(request, promotion);
        
        // Cập nhật lại chi nhánh nếu Super Admin muốn đổi rạp cho bài viết
        if (isSuperAdmin(getCurrentUser()) && request.getCinemaItemId() != null) {
            promotion.setCinemaItem(cinemaItemRepository.findById(request.getCinemaItemId()).orElse(null));
        }

        return promotionRepository.save(promotion);
    }

    @Override
    @Transactional
    public void deletePromotion(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sự kiện không tồn tại"));
        
        validateAdminPermission(promotion.getCinemaItem() != null ? promotion.getCinemaItem().getId() : null);
        promotionRepository.delete(promotion);
    }

    // --- HELPER METHODS ĐÃ ĐƯỢC TỐI ƯU ---

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập hết hạn, vui lòng login lại!"));
    }

    /**
     * Fix: Nhận diện cả "SUPER_ADMIN" và "ROLE_SUPER_ADMIN"
     */
    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equalsIgnoreCase("SUPER_ADMIN") 
                            || r.getRoleName().equalsIgnoreCase("ROLE_SUPER_ADMIN"));
    }

    private void validateAdminPermission(Long targetCinemaId) {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return; // Sếp tổng được quyền đi qua

        if (user.getManagedCinemaItemId() == null || !user.getManagedCinemaItemId().equals(targetCinemaId)) {
            throw new RuntimeException("Bạn không có quyền thao tác trên bài viết của chi nhánh rạp khác!");
        }
    }

    private void mapRequestToEntity(PromotionRequest request, Promotion promotion) {
        promotion.setTitle(request.getTitle());
        promotion.setContent(request.getContent());
        promotion.setThumbnail(request.getImage());
        
        // Gán Voucher nếu có
        if (request.getVoucherId() != null) {
            promotion.setVoucher(voucherRepository.findById(request.getVoucherId()).orElse(null));
        }
        // Gán Phim nếu có
        if (request.getMovieId() != null) {
            promotion.setMovie(movieRepository.findById(request.getMovieId()).orElse(null));
        }
    }
}