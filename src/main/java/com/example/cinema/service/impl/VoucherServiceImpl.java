package com.example.cinema.service.impl;

import com.example.cinema.dto.VoucherRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final PromotionRepository promotionRepository;

    @Override
    public List<Voucher> getAllVouchers() {
        return voucherRepository.findAll();
    }

    @Override
    @Transactional
    public Voucher createVoucher(VoucherRequest request) {
        validateSuperAdmin(); 

        if (voucherRepository.existsByCode(request.getCode().toUpperCase())) {
            throw new RuntimeException("Mã giảm giá '" + request.getCode().toUpperCase() + "' đã tồn tại!");
        }

        Voucher voucher = Voucher.builder()
                .code(request.getCode().toUpperCase())
                .title(request.getTitle())
                .description(request.getDescription())
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount())
                .usageLimit(request.getUsageLimit())
                .usedCount(0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        // Gắn vào Sự kiện (Promotion)
        if (request.getPromotionId() != null) {
            Promotion promotion = promotionRepository.findById(request.getPromotionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với ID: " + request.getPromotionId()));
            voucher.setPromotion(promotion);
        }

        return voucherRepository.save(voucher);
    }

    @Override
@Transactional
public Voucher validateAndGetVoucher(String code, Long cinemaItemId, Double currentTotal) {

    Voucher v = voucherRepository.findByCode(code.toUpperCase())
            .orElseThrow(() -> new ResourceNotFoundException("Mã giảm giá không hợp lệ!"));

    LocalDateTime now = LocalDateTime.now();

    if (v.getStartDate() != null && now.isBefore(v.getStartDate())) {
        throw new RuntimeException("Mã giảm giá chưa đến thời gian áp dụng!");
    }

    if (v.getEndDate() != null && now.isAfter(v.getEndDate())) {
        throw new RuntimeException("Mã giảm giá đã hết hạn sử dụng!");
    }

    if (v.getUsedCount() >= v.getUsageLimit()) {
        throw new RuntimeException("Mã giảm giá đã hết lượt sử dụng!");
    }

    if (currentTotal < v.getMinOrderAmount()) {
        throw new RuntimeException(
                "Đơn hàng chưa đủ điều kiện tối thiểu (" +
                String.format("%,.0f", v.getMinOrderAmount()) + "đ)");
    }

    // ===== LẤY USER ĐANG LOGIN =====
    String email = SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();

    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));

    // ===== KIỂM TRA USER CÓ VOUCHER KHÔNG =====
    boolean hasVoucher = user.getVouchers()
            .stream()
            .anyMatch(uv -> uv.getId().equals(v.getId()));

    if (!hasVoucher) {
        throw new RuntimeException("Bạn chưa lưu mã này trong kho!");
    }

    // ===== XOÁ KHỎI KHO USER =====
    user.getVouchers().removeIf(uv -> uv.getId().equals(v.getId()));

    userRepository.save(user);

    return v;
}
    @Override
    @Transactional
    public void saveVoucherToUser(Long userId, Long voucherId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Mã giảm giá không tồn tại"));

        if (voucher.getEndDate() != null && LocalDateTime.now().isAfter(voucher.getEndDate())) {
            throw new RuntimeException("Mã này đã hết hạn, không thể lưu!");
        }

        if (user.getVouchers().stream().anyMatch(v -> v.getId().equals(voucherId))) {
            throw new RuntimeException("Mã này đã có trong kho ưu đãi của bạn!");
        }

        user.getVouchers().add(voucher);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public Voucher updateVoucher(Long id, VoucherRequest request) {
        validateSuperAdmin(); 

        Voucher voucher = voucherRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy voucher với ID: " + id));

        String newCode = request.getCode().toUpperCase();
        if (!voucher.getCode().equals(newCode)) {
            if (voucherRepository.existsByCode(newCode)) {
                throw new RuntimeException("Mã giảm giá '" + newCode + "' đã tồn tại trên hệ thống!");
            }
        }

        voucher.setCode(newCode);
        voucher.setTitle(request.getTitle());
        voucher.setDescription(request.getDescription());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setMinOrderAmount(request.getMinOrderAmount());
        voucher.setUsageLimit(request.getUsageLimit());
        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());

        if (request.getPromotionId() != null) {
            Promotion promotion = promotionRepository.findById(request.getPromotionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với ID: " + request.getPromotionId()));
            voucher.setPromotion(promotion);
        } else {
            voucher.setPromotion(null);
        }

        return voucherRepository.save(voucher);
    }

    @Override
    public List<Voucher> getVouchersByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
        return user.getVouchers();
    }

    @Override
    public List<Voucher> getAvailableVouchers(Long promotionId) {
        // Sử dụng hàm Query custom đã fix trong Repository
        return voucherRepository.findActiveVouchersByPromotionId(promotionId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        validateSuperAdmin();
        Voucher v = voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá để xóa"));
        voucherRepository.delete(v);
    }

    private void validateSuperAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Vui lòng đăng nhập để tiếp tục!"));

        boolean isSuper = user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equalsIgnoreCase("SUPER_ADMIN") 
                            || r.getRoleName().equalsIgnoreCase("ROLE_SUPER_ADMIN"));

        if (!isSuper) {
            throw new RuntimeException("Quyền truy cập bị từ chối: Yêu cầu quyền Super Admin!");
        }
    }

    @Transactional
public void redeemVoucher(Long voucherId) {

    // lấy user đang login
    String email = SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getName();

    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));

    Voucher voucher = voucherRepository.findById(voucherId)
            .orElseThrow(() -> new ResourceNotFoundException("Voucher không tồn tại"));

    // ❌ voucher không cho đổi điểm
    if (voucher.getCostPoints() == null || voucher.getCostPoints() <= 0) {
        throw new RuntimeException("Voucher này không thể đổi bằng điểm");
    }

    // ❌ không đủ điểm
    if (user.getPoints() < voucher.getCostPoints()) {
        throw new RuntimeException("Không đủ điểm");
    }

    // ❌ đổi rồi
    boolean existed = user.getVouchers()
            .stream()
            .anyMatch(v -> v.getId().equals(voucherId));

    if (existed) {
        throw new RuntimeException("Bạn đã đổi voucher này rồi");
    }

    // ✅ trừ điểm
    user.setPoints(user.getPoints() - voucher.getCostPoints());

    // ✅ thêm voucher vào user
    user.getVouchers().add(voucher);

    userRepository.save(user);
}
}