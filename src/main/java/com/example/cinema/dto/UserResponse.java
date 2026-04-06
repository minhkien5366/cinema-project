package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String mobileNumber;
    private String avatar;
    private String gender;       // THÊM DÒNG NÀY
    private LocalDate dateOfBirth; // THÊM DÒNG NÀY
    private Set<String> roles;
}