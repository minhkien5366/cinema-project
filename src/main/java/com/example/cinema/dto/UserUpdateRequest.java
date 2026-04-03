package com.example.cinema.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UserUpdateRequest {
    private String firstName;
    private String lastName;
    private String mobileNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String avatar; // Đã thêm trường này
}