package com.example.cinema.service;

import com.example.cinema.dto.RevenueDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportService {
    List<RevenueDTO> getRevenue(LocalDateTime start, LocalDateTime end);
}