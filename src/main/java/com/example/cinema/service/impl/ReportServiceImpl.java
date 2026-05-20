package com.example.cinema.service.impl;

import com.example.cinema.dto.MovieRatingDTO;
import com.example.cinema.repository.OrderRepository;
import com.example.cinema.repository.ReviewRepository;
import com.example.cinema.service.ReportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.*;
@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderRepository orderRepository;
@Autowired
        private ReviewRepository reviewRepository;

    @Override
    public ByteArrayInputStream exportRevenueReport(Long cinemaId, LocalDateTime start, LocalDateTime end) throws IOException {
        var orders = orderRepository.findByCinemaItemIdAndCreatedAtBetweenAndStatus(cinemaId, start, end, "PAID");

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Doanh Thu");
            
            // Header
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Mã Đơn", "Ngày", "Số Tiền", "Phương Thức"};
            for (int i = 0; i < headers.length; i++) headerRow.createCell(i).setCellValue(headers[i]);

            // Data
            int rowIdx = 1;
            for (var o : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(o.getId());
                row.createCell(1).setCellValue(o.getCreatedAt().toString());
                row.createCell(2).setCellValue(o.getTotalAmount());
                row.createCell(3).setCellValue(o.getPaymentMethod());
            }
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Override
    public List<Map<String, Object>> getCinemaRankingData(LocalDateTime start, LocalDateTime end) {
        return orderRepository.getCinemaRanking(start, end).stream().map(obj -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", obj[0]);
            map.put("revenue", obj[1]);
            return map;
        }).collect(Collectors.toList());
    }

    @Override
public List<MovieRatingDTO> getMovieStatistics() {
    return reviewRepository.getTopRatedMovies().stream()
        .map(obj -> {
            String title = (String) obj[0];
            // Ép kiểu an toàn cho Double/Long (đề phòng trường hợp database trả về kiểu khác)
            Double avg = obj[1] instanceof Number ? ((Number) obj[1]).doubleValue() : 0.0;
            Long count = obj[2] instanceof Number ? ((Number) obj[2]).longValue() : 0L;
            return new MovieRatingDTO(title, avg, count);
        })
        .collect(Collectors.toList());
}
}