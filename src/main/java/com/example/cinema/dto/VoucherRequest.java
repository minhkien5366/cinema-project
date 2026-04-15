package com.example.cinema.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherRequest {

    @NotBlank(message = "Mã voucher không được để trống!")
    private String code;

    @NotBlank(message = "Tiêu đề voucher không được để trống!")
    private String title;

    // --- BỔ SUNG TRƯỜNG NÀY ĐỂ HẾT LỖI getDescription() ---
    private String description;

    @NotNull(message = "Giá trị giảm không được để trống!")
    @Min(value = 0, message = "Giá trị giảm không được âm")
    private Double discountValue;

    @Min(value = 0, message = "Đơn hàng tối thiểu không được âm")
    private Double minOrderAmount;

    @NotNull(message = "Số lượng mã không được để trống!")
    @Min(value = 1, message = "Số lượng mã tối thiểu là 1")
    private Integer usageLimit;

    @NotNull(message = "Ngày bắt đầu không được để trống!")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày hết hạn không được để trống!")
    private LocalDateTime endDate;

    private Long cinemaItemId; // NULL = Global (Super Admin), có ID = Theo rạp (Admin)
}