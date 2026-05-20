package com.example.cinema.controller;

import com.example.cinema.service.ReportService;
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

    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam Long cinemaId, 
            @RequestParam String start, 
            @RequestParam String end) throws IOException {

        // Gọi service để lấy luồng dữ liệu Excel
        var inputStream = reportService.exportRevenueReport(
                cinemaId, 
                LocalDateTime.parse(start), 
                LocalDateTime.parse(end)
        );

        // Trả về file dưới dạng download
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
}