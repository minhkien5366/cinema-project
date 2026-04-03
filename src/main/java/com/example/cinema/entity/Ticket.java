package com.example.cinema.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Data
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Double price;
    private String status;
    private String bookingCode;

    @ManyToOne @JoinColumn(name = "seat_id") private Seat seat;
    @ManyToOne @JoinColumn(name = "showtime_id") private Showtime showtime;
    @ManyToOne @JoinColumn(name = "user_id") private User user;

    @CreationTimestamp
    private LocalDateTime createdAt;
}