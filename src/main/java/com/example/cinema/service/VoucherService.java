package com.example.cinema.service;

import com.example.cinema.dto.VoucherRequest;
import com.example.cinema.entity.Voucher;
import java.util.List;

public interface VoucherService {
    // Admin lấy danh sách mã theo quyền
    List<Voucher> getAllVouchers();

    // Khách hàng lấy danh sách mã khả dụng của rạp
    List<Voucher> getAvailableVouchers(Long cinemaItemId);

    Voucher createVoucher(VoucherRequest request);

    void deleteVoucher(Long id);

    // Hàm quan trọng để OrderService gọi khi khách đặt vé
    Voucher validateAndGetVoucher(String code, Long cinemaItemId, Double currentTotal);
}