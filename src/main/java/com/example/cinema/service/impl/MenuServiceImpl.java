package com.example.cinema.service.impl;

import com.example.cinema.dto.MenuRequest;
import com.example.cinema.dto.MenuResponse;
import com.example.cinema.entity.Menu;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.MenuRepository;
import com.example.cinema.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuRepository menuRepository;

    @Override
    public List<MenuResponse> getMenuTree() {
        // Lấy các menu gốc (không có cha)
        List<Menu> rootMenus = menuRepository.findByParentIsNullOrderBySortOrderAsc();
        return rootMenus.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MenuResponse createMenu(MenuRequest request) {
        Menu menu = new Menu();
        mapRequestToEntity(request, menu);
        return convertToResponse(menuRepository.save(menu));
    }

    @Override
    @Transactional
    public MenuResponse updateMenu(Long id, MenuRequest request) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu không tồn tại"));
        mapRequestToEntity(request, menu);
        return convertToResponse(menuRepository.save(menu));
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        menuRepository.deleteById(id);
    }

    private void mapRequestToEntity(MenuRequest request, Menu menu) {
        menu.setName(request.getName());
        menu.setSlug(request.getSlug());
        menu.setUrl(request.getUrl());
        menu.setPosition(request.getPosition());
        menu.setSortOrder(request.getSortOrder());
        menu.setStatus(request.getStatus());

        if (request.getParentId() != null) {
            Menu parent = menuRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Menu cha không tồn tại"));
            menu.setParent(parent);
        } else {
            menu.setParent(null);
        }
    }

    private MenuResponse convertToResponse(Menu menu) {
        // Đệ quy để lấy toàn bộ menu con
        List<Menu> children = menuRepository.findByParentId(menu.getId());
        List<MenuResponse> childrenResponses = children.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return MenuResponse.builder()
                .id(menu.getId())
                .name(menu.getName())
                .slug(menu.getSlug())
                .url(menu.getUrl())
                .position(menu.getPosition())
                .sortOrder(menu.getSortOrder())
                .status(menu.getStatus())
                .children(childrenResponses)
                .build();
    }
}