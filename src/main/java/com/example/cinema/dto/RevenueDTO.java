package com.example.cinema.dto;

import java.math.BigDecimal;
import java.sql.Date;

public class RevenueDTO {

    private Date date;
    private BigDecimal totalRevenue;
    private Long totalOrders;

    public RevenueDTO(Date date, BigDecimal totalRevenue, Long totalOrders) {
        this.date = date;
        this.totalRevenue = totalRevenue;
        this.totalOrders = totalOrders;
    }

    public Date getDate() {
        return date;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public Long getTotalOrders() {
        return totalOrders;
    }
}