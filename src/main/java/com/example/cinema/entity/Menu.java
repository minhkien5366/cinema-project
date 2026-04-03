package com.example.cinema.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity // <--- BẮT BUỘC phải có để sửa lỗi "Not a managed type"
@Table(name = "menus")
@Data
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String slug;

    private String url;

    /**
     * Quan hệ tự tham chiếu (Self-reference) cho menu đa cấp.
     * Ánh xạ với cột parent_id trong database của bạn.
     */
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Menu parent;

    private String position;
    private Integer sortOrder;
    private String status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}