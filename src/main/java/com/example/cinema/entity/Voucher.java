package com.example.cinema.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // Ví dụ: GIAM50K, WELCOME

    private String title;
    private String description;
    
    private Double discountValue;  // Số tiền giảm (VD: 20000.0)
    private Double minOrderAmount; // Điều kiện đơn hàng tối thiểu
    
    private Integer usageLimit;    // Tổng số lượt mã
    private Integer usedCount = 0; // Đã dùng bao nhiêu lượt

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @ManyToOne
    @JoinColumn(name = "cinema_item_id")
    @JsonIgnoreProperties({"rooms", "showtimes"})
    private CinemaItem cinemaItem; // NULL = Toàn hệ thống, NOT NULL = Theo chi nhánh

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;
}