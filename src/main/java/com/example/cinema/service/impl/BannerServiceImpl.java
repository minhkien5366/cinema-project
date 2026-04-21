package com.example.cinema.service.impl;

import com.example.cinema.dto.BannerRequest;
import com.example.cinema.entity.Banner;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.BannerRepository;
import com.example.cinema.service.BannerService;
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
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final String uploadDir = "uploads/banners/";

    @Override
    public List<Banner> getAllBanners() {
        return bannerRepository.findAll();
    }

    @Override
    public List<Banner> getActiveBanners() {
        return bannerRepository.findByStatusOrderBySortOrderAsc("ACTIVE");
    }

    @Override
    public Banner getBannerById(Long id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Banner ID: " + id));
    }

    @Override
    @Transactional
    public Banner createBanner(BannerRequest request, MultipartFile file) {
        Banner banner = new Banner();
        mapRequestToEntity(request, banner);

        if (file != null && !file.isEmpty()) {
            banner.setImageUrl(saveFile(file));
        }
        return bannerRepository.save(banner);
    }

    @Override
    @Transactional
    public Banner updateBanner(Long id, BannerRequest request, MultipartFile file) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Banner"));
        
        mapRequestToEntity(request, banner);

        if (file != null && !file.isEmpty()) {
            deleteOldFile(banner.getImageUrl()); // Xóa ảnh cũ
            banner.setImageUrl(saveFile(file)); // Lưu ảnh mới
        }
        return bannerRepository.save(banner);
    }

    @Override
    @Transactional
    public void deleteBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Banner"));
        
        deleteOldFile(banner.getImageUrl());
        bannerRepository.delete(banner);
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
            throw new RuntimeException("Lỗi lưu file banner: " + e.getMessage());
        }
    }

    private void deleteOldFile(String fileName) {
        if (fileName != null) {
            try {
                Files.deleteIfExists(Paths.get(uploadDir + fileName));
            } catch (IOException ignored) {}
        }
    }

    private void mapRequestToEntity(BannerRequest request, Banner banner) {
        banner.setTitle(request.getTitle());
        banner.setLinkUrl(request.getLinkUrl());
        banner.setPosition(request.getPosition());
        banner.setStatus(request.getStatus());
        banner.setSortOrder(request.getSortOrder());
    }
}