package com.example.cinema.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "seats")
@Data
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;       // Ví dụ: "A1", "B5"
    private String seatRow;    // Ví dụ: "A", "B"
    private String seatNumber; // Ví dụ: "1", "2"
    private String seatType;   // "NORMAL" hoặc "VIP"
    private Double price;
    
    // Thêm trường này để Frontend biết ghế có trống không
    // Mặc định khi tạo ghế là "AVAILABLE"
    @Column(columnDefinition = "varchar(20) default 'AVAILABLE'")
    private String status = "AVAILABLE"; 

    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
