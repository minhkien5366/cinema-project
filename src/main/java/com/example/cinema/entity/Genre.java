package com.example.cinema.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "genres")
@Data
public class Genre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // 🎯 THÊM MỚI: Mối quan hệ đảo chiều từ Genre sang Movie
    @ManyToMany(mappedBy = "genres", fetch = FetchType.LAZY)
    @ToString.Exclude          // Tránh vòng lặp vô hạn lombok
    @EqualsAndHashCode.Exclude // Tránh vòng lặp vô hạn lombok
    private Set<Movie> movies = new HashSet<>();
}