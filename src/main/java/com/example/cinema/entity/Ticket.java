package com.example.cinema.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Double price;
    private String status; // BOOKED, PAID, CANCELLED
    private String bookingCode;

    @ManyToOne 
    @JoinColumn(name = "seat_id") 
    @JsonIgnoreProperties("room")
    private Seat seat;

    @ManyToOne 
    @JoinColumn(name = "showtime_id") 
    @JsonIgnoreProperties({"cinemaItem", "room"})
    private Showtime showtime;

    @ManyToOne 
    @JoinColumn(name = "user_id") 
    @JsonIgnoreProperties({"password", "roles", "managedCinemaItemId"})
    private User user;

    @CreationTimestamp
    private LocalDateTime createdAt;
}