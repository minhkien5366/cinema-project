package com.example.cinema.service.impl;

import com.example.cinema.dto.ComboAdminResponse;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.CinemaComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CinemaComboServiceImpl implements CinemaComboService {

    private final CinemaComboRepository cinemaComboRepository;
    private final ComboRepository comboRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final UserRepository userRepository;

    // ================= GET ADMIN COMBOS =================

    @Override
    public List<ComboAdminResponse> getCombosForAdmin() {

        User user = getCurrentUser();
        Long cinemaItemId = user.getManagedCinemaItemId();

        List<Combo> allCombos = comboRepository.findAll();

        List<CinemaCombo> configs =
                cinemaComboRepository.findByCinemaItemId(cinemaItemId);

        return allCombos.stream().map(combo -> {

            CinemaCombo config = configs.stream()
                    .filter(c -> c.getCombo().getId().equals(combo.getId()))
                    .findFirst()
                    .orElse(null);

            boolean available =
                    config == null || config.isActive();

            return ComboAdminResponse.builder()
                    .id(combo.getId())
                    .name(combo.getName())
                    .description(combo.getDescription())
                    .imageUrl(combo.getImageUrl())
                    .price(combo.getPrice())
                    .isAvailable(available)
                    .build();

        }).collect(Collectors.toList());
    }

    // ================= TOGGLE =================

    @Override
    @Transactional
    public Boolean toggleCombo(Long comboId) {

        User user = getCurrentUser();
        Long cinemaItemId = user.getManagedCinemaItemId();

        CinemaCombo cinemaCombo =
                cinemaComboRepository
                        .findByCinemaItemIdAndComboId(cinemaItemId, comboId)
                        .orElse(null);

        if (cinemaCombo == null) {

            CinemaItem cinemaItem =
                    cinemaItemRepository.findById(cinemaItemId)
                            .orElseThrow();

            Combo combo =
                    comboRepository.findById(comboId)
                            .orElseThrow();

            cinemaCombo = new CinemaCombo();
            cinemaCombo.setCinemaItem(cinemaItem);
            cinemaCombo.setCombo(combo);
            cinemaCombo.setActive(false);

        } else {
            cinemaCombo.setActive(!cinemaCombo.isActive());
        }

        cinemaComboRepository.save(cinemaCombo);

        return cinemaCombo.isActive(); // ⭐ RETURN STATE
    }

    // ================= CUSTOMER =================

    @Override
    public List<Combo> getActiveCombosForCinema(Long cinemaItemId) {

        List<Long> disabledIds =
                cinemaComboRepository
                        .findByCinemaItemIdAndActiveFalse(cinemaItemId)
                        .stream()
                        .map(cc -> cc.getCombo().getId())
                        .toList();

        return comboRepository.findAll()
                .stream()
                .filter(c -> !disabledIds.contains(c.getId()))
                .toList();
    }

    private User getCurrentUser() {
        String email =
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));
    }
}