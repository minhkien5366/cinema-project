package com.example.cinema.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double totalAmount;
    private String status;         // PENDING, PAID, CANCELLED
    private String paymentMethod;  

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"password", "roles", "managedCinemaItemId"})
    private User user;

    // QUAN TRỌNG: Để Admin chi nhánh lọc đơn hàng
    @ManyToOne
    @JoinColumn(name = "cinema_item_id")
    private CinemaItem cinemaItem;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("order")
    private List<OrderDetail> orderDetails;

    @CreationTimestamp
    private LocalDateTime createdAt;
}