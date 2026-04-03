package com.example.cinema.controller;

import com.example.cinema.dto.*;
import com.example.cinema.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
// @CrossOrigin(origins = "http://localhost:3000")
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
}