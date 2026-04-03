package com.example.cinema.service;

import com.example.cinema.dto.BannerRequest;
import com.example.cinema.entity.Banner;
import java.util.List;

public interface BannerService {
    // Lấy tất cả banner (Cho Admin quản lý)
    List<Banner> getAllBanners();

    // Lấy banner đang hoạt động (Cho người dùng xem)
    List<Banner> getActiveBanners();

    // Lấy chi tiết 1 banner
    Banner getBannerById(Long id);

    // Thêm mới
    Banner createBanner(BannerRequest request);

    // Cập nhật
    Banner updateBanner(Long id, BannerRequest request);

    // Xóa
    void deleteBanner(Long id);
}