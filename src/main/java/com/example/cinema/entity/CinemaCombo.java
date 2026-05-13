package com.example.cinema.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "cinema_combos",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"cinema_item_id", "combo_id"}
                )
        }
)
@Data
public class CinemaCombo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_item_id")
    private CinemaItem cinemaItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    // ⭐ dùng primitive boolean (KHÔNG dùng Boolean)
    @Column(nullable = false)
    private boolean active = true;
}