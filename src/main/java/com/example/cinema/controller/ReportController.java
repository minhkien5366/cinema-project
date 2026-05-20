package com.example.cinema.controller;

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

    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
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
}