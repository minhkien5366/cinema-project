package com.example.cinema.repository;

import com.example.cinema.entity.Order;
import com.example.cinema.projection.RevenueProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Order, Long> {

    @Query(value = """
        SELECT
            DATE(o.created_at) AS date,
            SUM(o.total_amount) AS totalRevenue,
            COUNT(o.id) AS totalOrders
        FROM orders o
        WHERE o.status = 'PAID'
        AND o.created_at BETWEEN :start AND :end
        GROUP BY DATE(o.created_at)
        ORDER BY DATE(o.created_at)
    """, nativeQuery = true)
    List<RevenueProjection> getRevenueNative(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}