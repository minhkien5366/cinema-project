package com.example.cinema.projection;

import java.math.BigDecimal;
import java.sql.Date;

public interface RevenueProjection {
    Date getDate();
    BigDecimal getTotalRevenue();
    Long getTotalOrders();
}