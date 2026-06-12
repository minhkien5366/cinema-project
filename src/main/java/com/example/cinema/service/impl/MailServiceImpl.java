package com.example.cinema.service.impl;

import com.example.cinema.entity.Order;
import com.example.cinema.entity.OrderDetail;
import com.example.cinema.entity.Ticket;
import com.example.cinema.service.MailService;
import com.example.cinema.repository.TicketRepository;
import com.example.cinema.repository.ComboRepository;
import com.example.cinema.entity.Combo;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Comparator; // 🔥 ĐÃ THÊM: Thư viện để sắp xếp lấy vé mới nhất

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private final TicketRepository ticketRepository;
    private final ComboRepository comboRepository;

    @Override
    @Async
    public void sendOrderConfirmation(Order order) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Chỉ định email người gửi
            helper.setFrom("kienphatanh@gmail.com", "A&K Cinema Ticket");
            
            helper.setTo(order.getUser().getEmail());
            helper.setSubject("A&K CINEMA - VÉ ĐIỆN TỬ XÁC NHẬN # " + order.getId());

            NumberFormat vnFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            
            String totalAmountFormatted = vnFormat.format(order.getTotalAmount());
            String customerName = order.getUser().getFirstName() + " " + order.getUser().getLastName();

            String movieTitle = "Vé Xem Phim";
            String roomName = "N/A";
            String showDate = "N/A";
            String showTime = "N/A";
            List<String> seatNames = new ArrayList<>();
            List<String> comboDetails = new ArrayList<>();
            String commonBookingCode = "N/A";

            // Dò tìm ID suất chiếu thực tế từ giỏ vé trong đơn hàng
            Long detectedShowtimeId = null;
            if (order.getOrderDetails() != null) {
                for (OrderDetail od : order.getOrderDetails()) {
                    if ("TICKET".equals(od.getItemType())) {
                        detectedShowtimeId = ticketRepository.findAll().stream()
                                .filter(t -> t.getSeat() != null && t.getSeat().getId().equals(od.getItemId()))
                                // 🔥 FIX: Sắp xếp đảo ngược để luôn lấy cái vé MỚI NHẤT vừa tạo
                                .sorted(Comparator.comparing(Ticket::getId).reversed()) 
                                .map(t -> t.getShowtime().getId())
                                .findFirst()
                                .orElse(null);
                        if (detectedShowtimeId != null) break;
                    }
                }
            }

            if (order.getOrderDetails() != null) {
                for (OrderDetail detail : order.getOrderDetails()) {
                    if ("TICKET".equals(detail.getItemType())) {
                        if (detectedShowtimeId != null) {
                            List<Ticket> tickets = ticketRepository.findBySeatIdAndShowtimeId(detail.getItemId(), detectedShowtimeId);
                            if (!tickets.isEmpty()) {
                                Ticket t = tickets.get(0);
                                if (t.getShowtime() != null) {
                                    movieTitle = t.getShowtime().getMovie() != null ? t.getShowtime().getMovie().getTitle() : movieTitle;
                                    roomName = t.getShowtime().getRoom() != null ? t.getShowtime().getRoom().getName() : roomName;
                                    showDate = t.getShowtime().getStartTime().format(dateFormatter);
                                    showTime = t.getShowtime().getStartTime().format(timeFormatter) + " - " + t.getShowtime().getEndTime().format(timeFormatter);
                                }
                                commonBookingCode = t.getBookingCode() != null ? t.getBookingCode() : commonBookingCode;
                                seatNames.add(t.getSeatName() != null ? t.getSeatName() : (t.getSeatRow() + t.getSeatNumber()));
                            }
                        }
                    } else if ("COMBO".equals(detail.getItemType())) {
                        String cName = comboRepository.findById(detail.getItemId()).map(Combo::getName).orElse("Combo Bắp Nước");
                        comboDetails.add(cName + " (x" + detail.getQuantity() + ")");
                    }
                }
            }

            Collections.sort(seatNames);
            String seatsString = seatNames.isEmpty() ? "N/A" : String.join(", ", seatNames);
            String combosString = comboDetails.isEmpty() ? "Không đăng ký bắp nước" : String.join("<br/> ", comboDetails);

            // Sinh mã QR động chứa mã đặt chỗ bookingCode phục vụ POS Quét máy tại quầy
            String qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=" + URLEncoder.encode(commonBookingCode, StandardCharsets.UTF_8);

            StringBuilder content = new StringBuilder();
            content.append("<div style='background-color: #050507; padding: 40px 15px; font-family: system-ui, -apple-system, sans-serif;'>");
            content.append("<div style='max-width: 480px; margin: 0 auto; background-color: #0b0b0f; border: 1px solid rgba(220,38,38,0.15); border-radius: 32px; overflow: hidden; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.7);'>");
            
            // Layout Cuống Vé Trên
            content.append("<div style='background: linear-gradient(135deg, #111, #1e050a); padding: 35px 25px; text-align: center; border-bottom: 2px dashed #050507;'>");
            content.append("<div style='font-size: 10px; font-weight: 900; letter-spacing: 5px; color: #dc2626; text-transform: uppercase; margin-bottom: 8px;'>A&K CINEMA TICKET</div>");
            content.append("<h2 style='margin: 0; font-size: 24px; font-weight: 900; color: #ffffff; text-transform: uppercase; letter-spacing: -0.5px;'>Vé Xem Phim Điện Tử</h2>");
            content.append("<div style='display: inline-block; margin-top: 15px; background-color: rgba(220,38,38,0.1); border: 1px solid rgba(220,38,38,0.2); padding: 6px 16px; border-radius: 20px; font-size: 11px; color: #ef4444; font-weight: 800; text-transform: uppercase;'>Mã Đơn: #").append(order.getId()).append("</div>");
            content.append("</div>");

            // Thân Cuống Vé
            content.append("<div style='padding: 30px 25px; background-color: #0b0b0f;'>");
            content.append("<span style='font-size: 9px; font-weight: 800; color: #dc2626; text-transform: uppercase; letter-spacing: 2px; display: block; margin-bottom: 6px;'>Tác Phẩm Điện Ảnh</span>");
            content.append("<h1 style='margin: 0 0 25px 0; font-size: 24px; font-weight: 900; color: #ffffff; line-height: 1.2; text-transform: uppercase; font-style: italic;'>").append(movieTitle).append("</h1>");
            
            content.append("<table style='width: 100%; font-size: 13px; color: #9ca3af;'>");
            content.append("<tr>");
            content.append("<td style='padding-bottom: 20px; width: 50%;'><span style='display:block; font-size:10px; color:#52525b; font-weight:bold; text-transform:uppercase;'>Ngày Chiếu</span><strong style='color:#f4f4f5; font-size:14px;'>").append(showDate).append("</strong></td>");
            content.append("<td style='padding-bottom: 20px; width: 50%;'><span style='display:block; font-size:10px; color:#52525b; font-weight:bold; text-transform:uppercase;'>Suất Chiếu</span><strong style='color:#f4f4f5; font-size:14px;'>").append(showTime).append("</strong></td>");
            content.append("</tr>");
            content.append("<tr>");
            content.append("<td><span style='display:block; font-size:10px; color:#52525b; font-weight:bold; text-transform:uppercase;'>Phòng Chiếu</span><strong style='color:#dc2626; font-size:18px; font-weight:900;'>").append(roomName).append("</strong></td>");
            content.append("<td><span style='display:block; font-size:10px; color:#52525b; font-weight:bold; text-transform:uppercase;'>Vị Trí Ghế</span><strong style='color:#ffffff; font-size:18px; font-weight:900;'>").append(seatsString).append("</strong></td>");
            content.append("</tr>");
            content.append("</table>");
            content.append("</div>");

            // Khối chứa mã QR Động
            content.append("<div style='padding: 35px 25px; background-color: #0e0e14; text-align: center; border-top: 1px dashed rgba(255,255,255,0.05); border-bottom: 1px dashed rgba(255,255,255,0.05);'>");
            content.append("<div style='font-size: 11px; font-weight: 800; color: #a1a1aa; letter-spacing: 2px; margin-bottom: 15px; text-transform: uppercase;'>Mã QR Soát Vé Tại Quầy</div>");
            content.append("<div style='display: inline-block; padding: 12px; background-color: #ffffff; border-radius: 20px; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.5);'><img src='").append(qrCodeUrl).append("' style='display: block;' width='160' height='160' alt='QR Code'/></div>");
            content.append("<div style='margin-top: 15px; font-size: 20px; font-weight: 900; color: #ffffff; letter-spacing: 5px;'>").append(commonBookingCode).append("</div>");
            content.append("<p style='margin: 10px 0 0 0; font-size: 11px; color: #52525b; font-weight: 500;'>Đưa mã này cho nhân viên soát vé để quét mã bàn giao vé cứng và bắp nước.</p>");
            content.append("</div>");

            // Khối kê chi tiết dịch vụ bắp nước đi kèm
            content.append("<div style='padding: 30px 25px; background-color: #0b0b0f;'>");
            content.append("<table style='width: 100%; font-size: 13px; color: #a1a1aa; border-collapse: collapse;'>");
            content.append("<tr><td style='padding-bottom: 8px;'>Khách Hàng:</td><td style='text-align: right; color: #fff; font-weight: bold;'>").append(customerName).append("</td></tr>");
            content.append("<tr><td style='padding-bottom: 8px; vertical-align: top;'>Dịch Vụ Đi Kèm:</td><td style='text-align: right; color: #fff; font-weight: bold; line-height: 1.4;'>").append(combosString).append("</td></tr>");
            content.append("<tr style='border-top: 1px solid rgba(255,255,255,0.05);'><td style='padding-top: 15px; font-size: 14px; font-weight: bold; color: #fff;'>Tổng Số Tiền Đã Trả:</td><td style='text-align: right; padding-top: 15px; font-size: 20px; font-weight: 900; color: #dc2626;'>").append(totalAmountFormatted).append("</td></tr>");
            content.append("</table>");
            content.append("</div>");

            // Footer
            content.append("<div style='background-color: #060608; padding: 20px; text-align: center; color: #3f3f46; font-size: 10px; font-weight: bold;'>");
            content.append("<p style='margin: 0 0 4px 0;'>HỆ THỐNG ĐIỆN TỬ PHÁT HÀNH VÉ TỰ ĐỘNG A&K CINEMA</p>");
            content.append("<p style='margin: 0;'>© 2026 A&K Cinema. All rights reserved.</p>");
            content.append("</div>");
            content.append("</div>");
            content.append("</div>");

            helper.setText(content.toString(), true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail: " + e.getMessage());
        }
    }
}