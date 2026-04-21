package com.example.cinema.service.impl;

import com.example.cinema.dto.VoucherRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.VoucherService;
import lombok.RequiredArgsConstructor;

import org.apache.coyote.BadRequestException;
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

        // Gắn vào Rạp cụ thể (nếu có)
        if (request.getCinemaItemId() != null) {
            voucher.setCinemaItem(cinemaItemRepository.findById(request.getCinemaItemId()).orElse(null));
        }

        return voucherRepository.save(voucher);
    }

    @Override
    public Voucher validateAndGetVoucher(String code, Long cinemaItemId, Double currentTotal) {
        Voucher v = voucherRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Mã giảm giá không hợp lệ!"));

        LocalDateTime now = LocalDateTime.now();
        
        // Kiểm tra thời gian
        if (v.getStartDate() != null && now.isBefore(v.getStartDate())) {
            throw new RuntimeException("Mã giảm giá chưa đến thời gian áp dụng!");
        }
        if (v.getEndDate() != null && now.isAfter(v.getEndDate())) {
            throw new RuntimeException("Mã giảm giá đã hết hạn sử dụng!");
        }
        
        // Kiểm tra số lượng
        if (v.getUsedCount() >= v.getUsageLimit()) {
            throw new RuntimeException("Mã giảm giá đã hết lượt sử dụng!");
        }
        
        // Kiểm tra điều kiện đơn hàng
        if (currentTotal < v.getMinOrderAmount()) {
            throw new RuntimeException("Đơn hàng chưa đủ điều kiện tối thiểu (" + String.format("%,.0f", v.getMinOrderAmount()) + "đ)");
        }
        
        // Kiểm tra chi nhánh rạp
        if (v.getCinemaItem() != null && !v.getCinemaItem().getId().equals(cinemaItemId)) {
            throw new RuntimeException("Mã này chỉ áp dụng tại rạp: " + v.getCinemaItem().getName());
        }
        
        return v;
    }

    @Override
    @Transactional
    public void saveVoucherToUser(Long userId, Long voucherId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Mã giảm giá không tồn tại"));

        // Kiểm tra hạn trước khi cho lưu
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
        // 1. Kiểm tra quyền Super Admin (giống lúc tạo)
        validateSuperAdmin(); 

        // 2. Kiểm tra voucher có tồn tại không
        Voucher voucher = voucherRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy voucher với ID: " + id));

        // 3. Kiểm tra trùng mã code (Nếu đổi sang mã mới thì mã đó không được trùng với ai khác)
        String newCode = request.getCode().toUpperCase();
        if (!voucher.getCode().equals(newCode)) {
            if (voucherRepository.existsByCode(newCode)) {
                throw new RuntimeException("Mã giảm giá '" + newCode + "' đã tồn tại trên hệ thống!");
            }
        }

        // 4. Cập nhật các thông tin cơ bản
        voucher.setCode(newCode);
        voucher.setTitle(request.getTitle());
        voucher.setDescription(request.getDescription());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setMinOrderAmount(request.getMinOrderAmount());
        voucher.setUsageLimit(request.getUsageLimit());
        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());
        // Giữ nguyên usedCount cũ để không làm sai lệch lịch sử sử dụng

        // 5. Cập nhật Sự kiện (Promotion) - Giống createVoucher
        if (request.getPromotionId() != null) {
            Promotion promotion = promotionRepository.findById(request.getPromotionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với ID: " + request.getPromotionId()));
            voucher.setPromotion(promotion);
        } else {
            voucher.setPromotion(null); // Nếu request gửi lên không có Promotion thì gỡ bỏ (tùy bặn)
        }

        // 6. Cập nhật Rạp áp dụng (CinemaItem) - Giống createVoucher
        if (request.getCinemaItemId() != null) {
            voucher.setCinemaItem(cinemaItemRepository.findById(request.getCinemaItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy rạp với ID: " + request.getCinemaItemId())));
        } else {
            voucher.setCinemaItem(null); // Nếu không chọn rạp thì coi như áp dụng toàn hệ thống
        }

        // 7. Lưu và trả về
        return voucherRepository.save(voucher);
    }

    @Override
    public List<Voucher> getVouchersByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
        return user.getVouchers();
    }

    /**
     * FIX CHÍNH: Lấy Voucher theo Promotion cho Client
     * Đã bao gồm check Now để tránh trả về mảng rỗng do lệch thời gian
     */
    @Override
    public List<Voucher> getAvailableVouchers(Long promotionId) {
        // Sử dụng LocalDateTime.now() để so khớp với Query trong Repository
        return voucherRepository.findByPromotionId(promotionId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        validateSuperAdmin();
        Voucher v = voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá để xóa"));
        
        // Trước khi xóa, cần gỡ mối quan hệ với User nếu có (tùy thuộc vào cấu trúc ManyToMany)
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
}