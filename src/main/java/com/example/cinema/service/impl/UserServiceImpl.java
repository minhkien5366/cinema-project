package com.example.cinema.service.impl;

import com.example.cinema.dto.RoleAssignmentRequest;
import com.example.cinema.dto.UserResponse;
import com.example.cinema.dto.UserUpdateRequest;
import com.example.cinema.entity.Role;
import com.example.cinema.entity.User;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.CinemaItemRepository;
import com.example.cinema.repository.RoleRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CinemaItemRepository cinemaItemRepository;

    @Override
    public UserResponse getMyProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UserUpdateRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng để cập nhật."));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setMobileNumber(request.getMobileNumber());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender());
        user.setAvatar(request.getAvatar());

        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    @Override
    public Page<UserResponse> getAllUsers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.searchUsers(keyword, pageable);
        return userPage.map(this::mapToResponse);
    }

    // --- HÀM NÂNG CAO: PHÂN QUYỀN VÀ GÁN RẠP ---
    @Override
    @Transactional
    public UserResponse assignRoleAndCinema(Long userId, RoleAssignmentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + userId));

        // 1. Chuyển đổi tên Role (String) sang thực thể Role
        Set<Role> roles = request.getRoles().stream()
                .map(roleName -> roleRepository.findByRoleName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Role không tồn tại: " + roleName)))
                .collect(Collectors.toSet());
        user.setRoles(roles);

        // 2. Gán rạp quản lý (Nếu có)
        if (request.getCinemaItemId() != null) {
            // Kiểm tra rạp phim có tồn tại không
            if (!cinemaItemRepository.existsById(request.getCinemaItemId())) {
                throw new ResourceNotFoundException("Rạp phim (Cinema Item) không tồn tại!");
            }
            user.setManagedCinemaItemId(request.getCinemaItemId());
        } else {
            user.setManagedCinemaItemId(null); // Gỡ quyền quản lý rạp nếu cần
        }

        return mapToResponse(userRepository.save(user));
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .mobileNumber(user.getMobileNumber())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .avatar(user.getAvatar())
                .managedCinemaItemId(user.getManagedCinemaItemId()) // Trả về thông tin rạp quản lý
                .roles(user.getRoles().stream()
                        .map(Role::getRoleName)
                        .collect(Collectors.toSet()))
                .build();
    }
}