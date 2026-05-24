package com.example.cinema.controller;

import com.example.cinema.service.FinanceService;
import com.example.cinema.service.ReportService;
import com.example.cinema.service.impl.ReviewServiceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;
    @Autowired
    private ReviewServiceImpl reviewService;
    @Autowired
    private FinanceService financeService;


    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam Long cinemaId, 
            // Thêm pattern vào đây để Spring tự parse chuỗi có dấu cách
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start, 
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) throws IOException {

        // Không dùng LocalDateTime.parse() nữa, dùng trực tiếp biến start/end
        var inputStream = reportService.exportRevenueReport(cinemaId, start, end);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=BaoCaoDoanhThu.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(inputStream));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    @GetMapping("/ranking")
    public ResponseEntity<?> getRanking(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        return ResponseEntity.ok(reportService.getCinemaRankingData(start, end));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
        @GetMapping("/stats")
        public ResponseEntity<?> getMovieStats() {
            return ResponseEntity.ok(reportService.getMovieStatistics());
        }


    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(
            @RequestParam(required = false) Long cinemaId
    ) {
        return ResponseEntity.ok(
                reportService.getAdminDashboard(cinemaId)
        );
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/revenue-7days")
    public ResponseEntity<?> chart(@RequestParam Long cinemaId) {

        return ResponseEntity.ok(
                reportService.getAdminRevenue7Days(cinemaId)
        );
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/finance")
    public ResponseEntity<?> getFinanceReport(@RequestParam String month) {
        return ResponseEntity.ok(financeService.getMonthlyFinance(month));
    }
    @GetMapping("/combo-best-selling")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<?> getBestSellingCombos(
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end
    ) {

        if (start == null || end == null) {
            LocalDateTime now = LocalDateTime.now();
            start = now.toLocalDate().atStartOfDay();      // 00:00
            end = now.toLocalDate().atTime(23, 59, 59);    // 23:59
        }

        return ResponseEntity.ok(
                reportService.getBestSellingCombos(cinemaId, start, end)
        );
    }
}