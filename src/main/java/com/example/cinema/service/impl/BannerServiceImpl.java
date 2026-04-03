package com.example.cinema.service.impl;

import com.example.cinema.dto.BannerRequest;
import com.example.cinema.entity.Banner;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.BannerRepository;
import com.example.cinema.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;

    @Override
    public List<Banner> getAllBanners() {
        return bannerRepository.findAll();
    }

    @Override
    public List<Banner> getActiveBanners() {
        // Sử dụng method bạn đã định nghĩa trong Repository
        return bannerRepository.findByStatusOrderBySortOrderAsc("ACTIVE");
    }

    @Override
    public Banner getBannerById(Long id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Banner với ID: " + id));
    }

    @Override
    @Transactional
    public Banner createBanner(BannerRequest request) {
        Banner banner = new Banner();
        mapRequestToEntity(request, banner);
        return bannerRepository.save(banner);
    }

    @Override
    @Transactional
    public Banner updateBanner(Long id, BannerRequest request) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Banner để cập nhật"));
        
        mapRequestToEntity(request, banner);
        return bannerRepository.save(banner);
    }

    @Override
    @Transactional
    public void deleteBanner(Long id) {
        if (!bannerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy Banner để xóa");
        }
        bannerRepository.deleteById(id);
    }

    // Helper method để map dữ liệu
    private void mapRequestToEntity(BannerRequest request, Banner banner) {
        banner.setTitle(request.getTitle());
        banner.setImageUrl(request.getImageUrl());
        banner.setLinkUrl(request.getLinkUrl());
        banner.setPosition(request.getPosition());
        banner.setStatus(request.getStatus());
        banner.setSortOrder(request.getSortOrder());
    }
}