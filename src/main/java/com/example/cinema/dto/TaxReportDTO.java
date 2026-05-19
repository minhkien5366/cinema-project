package com.example.cinema.dto;

import java.math.BigDecimal;

public record TaxReportDTO(
        BigDecimal totalRevenue,
        BigDecimal vat,
        BigDecimal revenueAfterTax
) {}