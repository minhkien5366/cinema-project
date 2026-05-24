package com.example.cinema.controller;

import com.example.cinema.dto.*;
import com.example.cinema.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(ApiResponse.<JwtResponse>builder()
                .status(200)
                .message("Đăng nhập thành công")
                .data(authService.login(loginRequest))
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@RequestBody RegisterRequest registerRequest) {
        String message = authService.register(registerRequest);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message(message)
                .data(null)
                .build());
    }

    // ==========================================
    // 🎯 THÊM MỚI: API Yêu cầu gửi OTP Quên Mật Khẩu
    // ==========================================
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestParam String email) {
        // Hàm này sẽ sinh mã OTP, lưu vào Cache/DB và gọi AuthMailService để gửi mail
        String message = authService.forgotPassword(email);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message(message)
                .data(null)
                .build());
    }

    // ==========================================
    // 🎯 THÊM MỚI: API Xác nhận OTP & Đặt lại mật khẩu mới
    // ==========================================
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        // Hàm này sẽ lấy OTP ra so sánh, nếu đúng thì mã hóa mật khẩu mới và lưu lại
        String message = authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message(message)
                .data(null)
                .build());
    }
}