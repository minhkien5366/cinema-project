package com.example.cinema.service.impl;

import com.example.cinema.dto.*;
import com.example.cinema.entity.*;
import com.example.cinema.repository.*;
import com.example.cinema.security.JwtTokenProvider;
import com.example.cinema.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public JwtResponse login(LoginRequest loginRequest) {
        // 1. Xác thực thông tin người dùng
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // 2. Tạo JWT Token
        String token = jwtTokenProvider.generateToken(authentication);

        // 3. Lấy danh sách quyền (Roles) trả về cho Frontend
        List<String> roles = authentication.getAuthorities().stream()
                .map(item -> item.getAuthority()).collect(Collectors.toList());

        return JwtResponse.builder()
                .token(token)
                .email(loginRequest.getEmail())
                .roles(roles)
                .type("Bearer")
                .build();
    }

   @Override
    @Transactional
    public String register(RegisterRequest registerRequest) {
        // 1. Kiểm tra email tồn tại
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email đã tồn tại!");
        }

        // 2. Tạo đối tượng User mới
        User user = new User();
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setMobileNumber(registerRequest.getMobileNumber());
        user.setGender(registerRequest.getGender());
        user.setDateOfBirth(registerRequest.getDateOfBirth());
        user.setAvatar(registerRequest.getAvatar());

        // 3. Xử lý gán quyền: LUÔN MẶC ĐỊNH LÀ ROLE_USER
        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy ROLE_USER trong hệ thống."));
        roles.add(userRole);

        user.setRoles(roles);
        userRepository.save(user);
        return "Đăng ký tài khoản thành công!";
    }
}