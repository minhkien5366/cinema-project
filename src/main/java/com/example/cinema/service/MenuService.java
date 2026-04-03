package com.example.cinema.service;

import com.example.cinema.dto.MenuRequest;
import com.example.cinema.dto.MenuResponse;
import java.util.List;

public interface MenuService {
    List<MenuResponse> getMenuTree(); // Lấy toàn bộ cấu trúc cây menu
    MenuResponse createMenu(MenuRequest request);
    MenuResponse updateMenu(Long id, MenuRequest request);
    void deleteMenu(Long id);
}