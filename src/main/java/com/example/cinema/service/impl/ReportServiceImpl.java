package com.example.cinema.service.impl;

import com.example.cinema.dto.RevenueDTO;
import com.example.cinema.repository.ReportRepository;
import com.example.cinema.projection.RevenueProjection;
import com.example.cinema.service.ReportService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;

    public ReportServiceImpl(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Override
    public List<RevenueDTO> getRevenue(java.time.LocalDateTime start,
                                       java.time.LocalDateTime end) {

        return reportRepository.getRevenueNative(start, end)
                .stream()
                .map(p -> new RevenueDTO(
                        p.getDate(),
                        p.getTotalRevenue(),
                        p.getTotalOrders()
                ))
                .toList();
    }
}