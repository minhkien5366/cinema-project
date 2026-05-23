package com.example.cinema.service.impl;

import com.example.cinema.dto.PromotionRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.CloudinaryService;
import com.example.cinema.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    public List<Promotion> getPromotionsForClient(Long cinemaItemId) {
        return promotionRepository.findByCinemaItem_IdOrCinemaItemIsNull(cinemaItemId);
    }

    @Override
    public List<Promotion> getAllPromotions() {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return promotionRepository.findAll();
        return promotionRepository.findByCinemaItem_Id(user.getManagedCinemaItemId());
    }

    @Override
    public Promotion getPromotionById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sự kiện không tồn tại!"));
    }

    // ================= CREATE =================

    @Override
    @Transactional
    public Promotion createPromotion(PromotionRequest request, MultipartFile file) {

        if (promotionRepository.findByTitle(request.getTitle()).isPresent()) {
            throw new RuntimeException("Tên khuyến mãi đã tồn tại!");
        }

        Promotion promotion = new Promotion();
        mapRequestToEntity(request, promotion);

        if (file != null && !file.isEmpty()) {
            try {
                String url = cloudinaryService.uploadImage(file, "promotions");
                promotion.setThumbnail(url);
            } catch (IOException e) {
                throw new RuntimeException("Upload ảnh thất bại: " + e.getMessage());
            }
        }

        return promotionRepository.save(promotion);
    }   
    // ================= UPDATE =================
    
    @Override
    @Transactional
    public Promotion updatePromotion(Long id, PromotionRequest request, MultipartFile file) {

        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện"));

        if (!promotion.getTitle().equalsIgnoreCase(request.getTitle())) {

            promotionRepository.findByTitle(request.getTitle())
                    .ifPresent(existing -> {
                        throw new RuntimeException("Tên khuyến mãi đã tồn tại!");
                    });
        }

        mapRequestToEntity(request, promotion);

        if (file != null && !file.isEmpty()) {
            try {
                if (promotion.getThumbnail() != null &&
                        promotion.getThumbnail().contains("cloudinary")) {
                    cloudinaryService.deleteImage(promotion.getThumbnail());
                }

                String url = cloudinaryService.uploadImage(file, "promotions");
                promotion.setThumbnail(url);

            } catch (IOException e) {
                throw new RuntimeException("Lỗi xử lý ảnh: " + e.getMessage());
            }
        }

        return promotionRepository.save(promotion);
    }
    
    // ================= DELETE =================

    @Override
    @Transactional
    public void deletePromotion(Long id) {

        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sự kiện không tồn tại"));

        try {
            if (promotion.getThumbnail() != null &&
                    promotion.getThumbnail().contains("cloudinary")) {

                cloudinaryService.deleteImage(promotion.getThumbnail());
            }
        } catch (IOException e) {
            System.err.println("Không thể xóa ảnh cloud: " + e.getMessage());
        }

        promotionRepository.delete(promotion);
    }

    // ================= HELPERS =================

    private void mapRequestToEntity(PromotionRequest request, Promotion promotion) {

        promotion.setTitle(request.getTitle());
        promotion.setContent(request.getContent());

        if (request.getMovieId() != null && request.getMovieId() != 0) {
            promotion.setMovie(
                    movieRepository.findById(request.getMovieId()).orElse(null)
            );
        } else {
            promotion.setMovie(null);
        }

        if (request.getCinemaItemId() != null && request.getCinemaItemId() != 0) {
            promotion.setCinemaItem(
                    cinemaItemRepository.findById(request.getCinemaItemId()).orElse(null)
            );
        } else {
            promotion.setCinemaItem(null);
        }
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r ->
                r.getRoleName().equalsIgnoreCase("SUPER_ADMIN") ||
                        r.getRoleName().equalsIgnoreCase("ROLE_SUPER_ADMIN"));
    }
}