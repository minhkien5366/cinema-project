package com.example.cinema.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "cinemas")
@Data
public class Cinema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
}