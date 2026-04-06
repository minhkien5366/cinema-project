package com.example.cinema.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Data
@Builder // Hỗ trợ tạo Object nhanh trong Service
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private Integer totalSeats;

    @ManyToOne(fetch = FetchType.EAGER) // EAGER để lấy luôn thông tin rạp khi gọi phòng
    @JoinColumn(name = "cinema_item_id")
    @JsonIgnoreProperties("rooms") // Ngăn vòng lặp JSON nếu CinemaItem cũng chứa danh sách Room
    private CinemaItem cinemaItem;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}