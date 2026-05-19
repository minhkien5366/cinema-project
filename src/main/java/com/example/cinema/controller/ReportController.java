package com.example.cinema.controller;

import com.example.cinema.dto.RevenueDTO;
import com.example.cinema.service.ReportService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/revenue")
    public List<RevenueDTO> getRevenue(
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end
    ) {
        return reportService.getRevenue(start, end);
    }
}