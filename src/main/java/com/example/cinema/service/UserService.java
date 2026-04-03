package com.example.cinema.service;

import com.example.cinema.dto.UserResponse;
import com.example.cinema.dto.UserUpdateRequest;
import org.springframework.data.domain.Page;

public interface UserService {
    // Lấy thông tin tài khoản đang đăng nhập
    UserResponse getMyProfile();

    // Cập nhật thông tin cá nhân
    UserResponse updateProfile(UserUpdateRequest request);

    // Tìm kiếm người dùng nâng cao (Chỉ dành cho ADMIN/SUPER_ADMIN)
    Page<UserResponse> getAllUsers(String keyword, int page, int size);
}