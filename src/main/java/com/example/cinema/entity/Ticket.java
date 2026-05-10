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

    // BOOKED | PAID | CANCELLED
    private String status;

    private String bookingCode;

    /*
     * IMPORTANT:
     * Seat có thể bị xóa nhưng vé phải còn
     * => ON DELETE SET NULL
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "seat_id",
            nullable = true,
            foreignKey = @ForeignKey(
                    foreignKeyDefinition =
                            "FOREIGN KEY (seat_id) REFERENCES seats(id) ON DELETE SET NULL"
            )
    )
    @JsonIgnoreProperties({"room"})
    private Seat seat;

    /*
     * Snapshot dữ liệu ghế
     * Giữ lịch sử khi seat bị xóa
     */
    private String seatRow;
    private String seatNumber;
    private String seatName;

    /*
     * Showtime
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id")
    @JsonIgnoreProperties({"cinemaItem", "room"})
    private Showtime showtime;

    /*
     * User mua vé
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"password", "roles", "managedCinemaItemId"})
    private User user;

    @CreationTimestamp
    private LocalDateTime createdAt;
}