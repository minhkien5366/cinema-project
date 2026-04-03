package com.example.cinema.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "movies")
@Data
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;
    
    private Integer duration;
    private String director;

    @Column(columnDefinition = "TEXT")
    private String cast;

    @ManyToOne
    @JoinColumn(name = "genre_id")
    private Genre genre;
    private Double rating;
    private String country;
    private String status;
    private String posterUrl;
    private String trailerUrl;
    private LocalDate releaseDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}