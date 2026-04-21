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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    
    private final String uploadDir = "uploads/promotions/";

    @Override
    public List<Promotion> getPromotionsForClient(Long cinemaItemId) {
        // Ưu tiên lấy tin của rạp khách đang chọn hoặc tin chung (null)
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

    @Override
    @Transactional
    public Promotion createPromotion(PromotionRequest request, MultipartFile file) {
        Promotion promotion = new Promotion();
        mapRequestToEntity(request, promotion);
        
        if (file != null && !file.isEmpty()) {
            promotion.setThumbnail(saveFile(file));
        }
        return promotionRepository.save(promotion);
    }

    @Override
    @Transactional
    public Promotion updatePromotion(Long id, PromotionRequest request, MultipartFile file) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện"));
        
        mapRequestToEntity(request, promotion);
        
        if (file != null && !file.isEmpty()) {
            deleteOldFile(promotion.getThumbnail());
            promotion.setThumbnail(saveFile(file));
        }
        return promotionRepository.save(promotion);
    }

    @Override
    @Transactional
    public void deletePromotion(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sự kiện không tồn tại"));
        deleteOldFile(promotion.getThumbnail());
        promotionRepository.delete(promotion);
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
            throw new RuntimeException("Lỗi upload ảnh sự kiện: " + e.getMessage());
        }
    }

    private void deleteOldFile(String fileName) {
        if (fileName != null) {
            try { Files.deleteIfExists(Paths.get(uploadDir + fileName)); } catch (IOException ignored) {}
        }
    }

    private void mapRequestToEntity(PromotionRequest request, Promotion promotion) {
        promotion.setTitle(request.getTitle());
        promotion.setContent(request.getContent());
        
        if (request.getMovieId() != null && request.getMovieId() != 0) {
            promotion.setMovie(movieRepository.findById(request.getMovieId()).orElse(null));
        } else {
            promotion.setMovie(null);
        }

        if (request.getCinemaItemId() != null && request.getCinemaItemId() != 0) {
            promotion.setCinemaItem(cinemaItemRepository.findById(request.getCinemaItemId()).orElse(null));
        } else {
            promotion.setCinemaItem(null);
        }
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User không tồn tại"));
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> 
            r.getRoleName().equalsIgnoreCase("SUPER_ADMIN") || 
            r.getRoleName().equalsIgnoreCase("ROLE_SUPER_ADMIN"));
    }
}