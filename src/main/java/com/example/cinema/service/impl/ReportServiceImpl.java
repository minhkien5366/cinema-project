package com.example.cinema.service.impl;

import com.example.cinema.dto.AdminDashboardDTO;
import com.example.cinema.dto.MovieRatingDTO;
import com.example.cinema.dto.RevenueChartDTO;
import com.example.cinema.entity.Order;
import com.example.cinema.repository.ShowtimeRepository;
import com.example.cinema.repository.OrderRepository;
import com.example.cinema.repository.ReviewRepository;
import com.example.cinema.service.ReportService;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    private static final double VAT_RATE = 0.10;

    // =========================
    // 📊 EXPORT REVENUE REPORT
    // =========================
    @Override
    public ByteArrayInputStream exportRevenueReport(
            Long cinemaId,
            LocalDateTime start,
            LocalDateTime end
    ) throws IOException {

        List<Order> orders;

        if (cinemaId != null) {
            orders = orderRepository
                    .findByCinemaItemIdAndCreatedAtBetweenAndStatus(
                            cinemaId, start, end, "PAID"
                    );
        } else {
            orders = orderRepository
                    .findByCreatedAtBetweenAndStatus(
                            start, end, "PAID"
                    );
        }

        try (
                Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {

            Sheet sheet = workbook.createSheet("Doanh Thu");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);

            String[] headers = {
                    "Mã Đơn",
                    "Rạp",
                    "Ngày",
                    "Tổng Tiền (Gross)",
                    "Thuế (VAT)",
                    "Doanh Thu Ròng",
                    "Phương Thức"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;

            double totalGross = 0;
            double totalTax = 0;
            double totalNet = 0;

            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            for (Order order : orders) {

                double gross = order.getTotalAmount() == null ? 0 : order.getTotalAmount();

                double tax = gross * VAT_RATE;
                double net = gross - tax;

                totalGross += gross;
                totalTax += tax;
                totalNet += net;

                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(order.getId());

                row.createCell(1).setCellValue(
                        order.getCinemaItem() != null
                                ? order.getCinemaItem().getName()
                                : "N/A"
                );

                row.createCell(2).setCellValue(
                        order.getCreatedAt().format(formatter)
                );

                row.createCell(3).setCellValue(gross);
                row.createCell(4).setCellValue(tax);
                row.createCell(5).setCellValue(net);

                row.createCell(6).setCellValue(
                        order.getPaymentMethod() != null
                                ? order.getPaymentMethod()
                                : "N/A"
                );
            }

            // TOTAL ROW
            Row totalRow = sheet.createRow(rowIdx + 1);

            totalRow.createCell(2).setCellValue("TỔNG CỘNG");
            totalRow.createCell(3).setCellValue(totalGross);
            totalRow.createCell(4).setCellValue(totalTax);
            totalRow.createCell(5).setCellValue(totalNet);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    // =========================
    // 🎬 CINEMA RANKING
    // =========================
    @Override
    public List<Map<String, Object>> getCinemaRankingData(
            LocalDateTime start,
            LocalDateTime end
    ) {

        return orderRepository
                .getCinemaRanking(start, end)
                .stream()
                .map(obj -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", obj[0]);
                    map.put("revenue", obj[1]);
                    return map;
                })
                .collect(Collectors.toList());
    }

    // =========================
    // 🎥 MOVIE STATS
    // =========================
    @Override
    public List<MovieRatingDTO> getMovieStatistics() {

        return reviewRepository
                .getTopRatedMovies()
                .stream()
                .map(obj -> new MovieRatingDTO(
                        (String) obj[0],
                        ((Number) obj[1]).doubleValue(),
                        ((Number) obj[2]).longValue()
                ))
                .collect(Collectors.toList());
    }

    // =========================
    // 📊 ADMIN DASHBOARD
    // =========================
    @Override
    public AdminDashboardDTO getAdminDashboard(Long cinemaId) {

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Double revenue = orderRepository.getTodayRevenue(startOfDay);
        Long tickets = orderRepository.countTodayTickets(startOfDay);
        Long showtimes = showtimeRepository.countTodayShowtimes();

        revenue = revenue == null ? 0 : revenue;
        tickets = tickets == null ? 0 : tickets;
        showtimes = showtimes == null ? 0 : showtimes;

        double occupancy = showtimes > 0
                ? (tickets * 100.0) / (showtimes * 100)
                : 0;

        return new AdminDashboardDTO(
                revenue,
                tickets,
                showtimes,
                Math.min(100, occupancy)
        );
    }

    // =========================
    // 📈 7 DAYS CHART
    // =========================
    @Override
    public List<RevenueChartDTO> getAdminRevenue7Days() {

        LocalDateTime start = LocalDateTime.now().minusDays(7);

        return orderRepository.revenue7Days(start)
                .stream()
                .map(o -> new RevenueChartDTO(
                        o[0].toString(),
                        ((Number) o[1]).doubleValue()
                ))
                .collect(Collectors.toList());
    }

    // =========================
    // 💰 FINANCE HELPERS (FIXED)
    // =========================

    private double calculateTax(Order order) {
        double gross = order.getTotalAmount() == null ? 0 : order.getTotalAmount();
        return gross * VAT_RATE;
    }

    private double calculateNetRevenue(Order order) {
        double gross = order.getTotalAmount() == null ? 0 : order.getTotalAmount();
        double tax = calculateTax(order);
        return gross - tax;
    }
}