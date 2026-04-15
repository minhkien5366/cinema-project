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
    private final CinemaItemRepository cinemaItemRepository;
    private final UserRepository userRepository;

    @Override
    public List<Voucher> getAllVouchers() {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return voucherRepository.findAll();
        return voucherRepository.findByCinemaItem_Id(user.getManagedCinemaItemId());
    }

    @Override
    public List<Voucher> getAvailableVouchers(Long cinemaItemId) {
        return voucherRepository.findAvailableVouchers(cinemaItemId);
    }

    @Override
    @Transactional
    public Voucher createVoucher(VoucherRequest request) {
        User user = getCurrentUser();
        
        if (voucherRepository.existsByCode(request.getCode().toUpperCase())) {
            throw new RuntimeException("Mã giảm giá này đã tồn tại!");
        }

        Voucher voucher = Voucher.builder()
                .code(request.getCode().toUpperCase())
                .title(request.getTitle())
                .description(request.getDescription()) // Đã hết lỗi undefined
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount())
                .usageLimit(request.getUsageLimit())
                .usedCount(0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        // --- FIX LỖI TẠI ĐÂY ---
        if (isSuperAdmin(user)) {
            // Nếu là Super Admin, cinemaItemId có thể null (mã chung) hoặc gán cho 1 rạp bất kỳ
            if (request.getCinemaItemId() != null) {
                voucher.setCinemaItem(cinemaItemRepository.findById(request.getCinemaItemId()).orElse(null));
            }
        } else {
            // Admin thường: Bắt buộc lấy rạp từ User
            Long managedId = user.getManagedCinemaItemId();
            if (managedId == null) {
                throw new RuntimeException("Lỗi: Tài khoản Admin của bạn chưa được gán rạp quản lý trong DB!");
            }
            voucher.setCinemaItem(cinemaItemRepository.findById(managedId).get());
        }

        return voucherRepository.save(voucher);
    }

    @Override
    public Voucher validateAndGetVoucher(String code, Long cinemaItemId, Double currentTotal) {
        Voucher v = voucherRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Mã giảm giá không hợp lệ!"));

        if (LocalDateTime.now().isBefore(v.getStartDate()) || LocalDateTime.now().isAfter(v.getEndDate())) {
            throw new RuntimeException("Mã giảm giá đã hết hạn!");
        }
        if (v.getUsedCount() >= v.getUsageLimit()) {
            throw new RuntimeException("Mã giảm giá đã hết lượt sử dụng!");
        }
        if (currentTotal < v.getMinOrderAmount()) {
            throw new RuntimeException("Đơn hàng chưa đạt giá trị tối thiểu " + v.getMinOrderAmount() + "đ");
        }
        if (v.getCinemaItem() != null && !v.getCinemaItem().getId().equals(cinemaItemId)) {
            throw new RuntimeException("Mã này không áp dụng cho rạp hiện tại!");
        }
        
        return v;
    }

    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher v = voucherRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã"));
        validateAdminPermission(v.getCinemaItem() != null ? v.getCinemaItem().getId() : null);
        voucherRepository.delete(v);
    }

    // --- HÀM CHECK QUYỀN ĐÃ ĐƯỢC FIX ---
    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equalsIgnoreCase("SUPER_ADMIN") 
                            || r.getRoleName().equalsIgnoreCase("ROLE_SUPER_ADMIN"));
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
    }

    private void validateAdminPermission(Long targetCinemaId) {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return;
        if (user.getManagedCinemaItemId() == null || !user.getManagedCinemaItemId().equals(targetCinemaId)) {
            throw new RuntimeException("Bạn không có quyền thao tác trên mã của chi nhánh khác!");
        }
    }
}