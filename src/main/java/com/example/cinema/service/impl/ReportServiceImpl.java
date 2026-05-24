package com.example.cinema.service.impl;

import com.example.cinema.dto.AdminDashboardDTO;
import com.example.cinema.dto.ComboReportResponse;
import com.example.cinema.dto.MovieRatingDTO;
import com.example.cinema.dto.RevenueChartDTO;
import com.example.cinema.entity.Order;
import com.example.cinema.repository.ShowtimeRepository;
import com.example.cinema.repository.OrderRepository;
import com.example.cinema.repository.ReviewRepository;
import com.example.cinema.service.ReportService;
import com.example.cinema.repository.OrderDetailRepository;

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

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    private static final double VAT_RATE = 0.10;


        @Override
        public ByteArrayInputStream exportRevenueReport(
                Long cinemaId,
                LocalDateTime start,
                LocalDateTime end
        ) throws IOException {

        List<Order> orders;

        if (cinemaId != null && cinemaId > 0) {

                orders = orderRepository
                        .findByCinemaItemIdAndCreatedAtBetweenAndStatus(
                                cinemaId,
                                start,
                                end,
                                "PAID"
                        );

        } else {

                orders = orderRepository
                        .findByCreatedAtBetweenAndStatus(
                                start,
                                end,
                                "PAID"
                        );
        }

        try (
                Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {

                Sheet sheet = workbook.createSheet("Bao Cao Doanh Thu");

                Font headerFont = workbook.createFont();
                headerFont.setBold(true);

                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFont(headerFont);

                DataFormat format = workbook.createDataFormat();

                CellStyle moneyStyle = workbook.createCellStyle();
                moneyStyle.setDataFormat(
                        format.getFormat("#,##0")
                );

                Row headerRow = sheet.createRow(0);

                String[] headers = {
                        "Mã Đơn",
                        "Rạp",
                        "Ngày Thanh Toán",
                        "Tổng Tiền",
                        "Thuế VAT",
                        "Doanh Thu Ròng",
                        "Phương Thức Thanh Toán"
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
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy HH:mm"
                        );

                for (Order order : orders) {

                double gross =
                        order.getTotalAmount() == null
                                ? 0
                                : order.getTotalAmount();

                double tax = gross * VAT_RATE;

                double net = gross - tax;

                totalGross += gross;
                totalTax += tax;
                totalNet += net;

                Row row = sheet.createRow(rowIdx++);

                row.createCell(0)
                        .setCellValue(order.getId());

                row.createCell(1)
                        .setCellValue(
                                order.getCinemaItem() != null
                                        ? order.getCinemaItem().getName()
                                        : "N/A"
                        );

                row.createCell(2)
                        .setCellValue(
                                order.getCreatedAt()
                                        .format(formatter)
                        );

                Cell grossCell = row.createCell(3);
                grossCell.setCellValue(gross);
                grossCell.setCellStyle(moneyStyle);

                Cell taxCell = row.createCell(4);
                taxCell.setCellValue(tax);
                taxCell.setCellStyle(moneyStyle);

                Cell netCell = row.createCell(5);
                netCell.setCellValue(net);
                netCell.setCellStyle(moneyStyle);

                row.createCell(6)
                        .setCellValue(
                                order.getPaymentMethod() != null
                                        ? order.getPaymentMethod()
                                        : "N/A"
                        );
                }

                Row totalRow = sheet.createRow(rowIdx + 1);

                Cell totalLabel = totalRow.createCell(2);
                totalLabel.setCellValue("TỔNG CỘNG");
                totalLabel.setCellStyle(headerStyle);

                Cell totalGrossCell = totalRow.createCell(3);
                totalGrossCell.setCellValue(totalGross);
                totalGrossCell.setCellStyle(moneyStyle);

                Cell totalTaxCell = totalRow.createCell(4);
                totalTaxCell.setCellValue(totalTax);
                totalTaxCell.setCellStyle(moneyStyle);

                Cell totalNetCell = totalRow.createCell(5);
                totalNetCell.setCellValue(totalNet);
                totalNetCell.setCellStyle(moneyStyle);

                for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                }

                workbook.write(out);

                return new ByteArrayInputStream(
                        out.toByteArray()
                );
        }
        }

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

        @Override
        public AdminDashboardDTO getAdminDashboard(Long cinemaId) {

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Double revenue;
        Long tickets;
        Long showtimes;

        if (cinemaId != null) {

                revenue = orderRepository.getTodayRevenueByCinema(cinemaId, startOfDay);
                tickets = orderRepository.countTodayTicketsByCinema(cinemaId, startOfDay);
                showtimes = showtimeRepository.countTodayShowtimesByCinema(cinemaId);

        } else {

                revenue = orderRepository.getTodayRevenue(startOfDay);
                tickets = orderRepository.countTodayTickets(startOfDay);
                showtimes = showtimeRepository.countTodayShowtimes();
        }

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

        @Override
        public List<RevenueChartDTO> getAdminRevenue7Days(Long cinemaId) {

        LocalDateTime start = LocalDateTime.now().minusDays(7);

        return orderRepository.revenue7DaysByCinema(cinemaId, start)
                .stream()
                .map(o -> new RevenueChartDTO(
                        o[0].toString(),
                        ((Number) o[1]).doubleValue()
                ))
                .collect(Collectors.toList());
        }

        private double calculateTax(Order order) {
                double gross = order.getTotalAmount() == null ? 0 : order.getTotalAmount();
                return gross * VAT_RATE;
        }

        private double calculateNetRevenue(Order order) {
                double gross = order.getTotalAmount() == null ? 0 : order.getTotalAmount();
                double tax = calculateTax(order);
                return gross - tax;
        }

        @Override
        public List<ComboReportResponse> getBestSellingCombos(Long cinemaId, LocalDateTime start, LocalDateTime end) {
        return orderDetailRepository.getBestSellingCombos(cinemaId, start, end);
        }
}