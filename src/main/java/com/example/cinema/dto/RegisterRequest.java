package com.example.cinema.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String mobileNumber;
    private String gender;
    private LocalDate dateOfBirth;
    private String avatar; // Đã thêm trường này
}