package com.example.cinema.service;

import com.example.cinema.dto.JwtResponse;
import com.example.cinema.dto.LoginRequest;
import com.example.cinema.dto.RegisterRequest;

public interface AuthService {
    JwtResponse login(LoginRequest loginRequest);
    String register(RegisterRequest registerRequest);
}