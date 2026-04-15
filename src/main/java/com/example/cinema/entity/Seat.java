package com.example.cinema.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    
    private String name;       
    private String seatRow;    
    private String seatNumber; 
    private String seatType;   
    private Double price;
    
    @Column(columnDefinition = "varchar(20) default 'AVAILABLE'")
    private String status = "AVAILABLE"; 

    @ManyToOne
    @JoinColumn(name = "room_id")
    @JsonIgnoreProperties("seats") 
    private Room room;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}