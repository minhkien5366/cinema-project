package com.example.cinema.service.impl;

import com.example.cinema.entity.Order;
import com.example.cinema.entity.OrderDetail;
import com.example.cinema.service.MailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Override
    @Async // Chạy ngầm để không ảnh hưởng đến tốc độ phản hồi thanh toán
    public void sendOrderConfirmation(Order order) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(order.getUser().getEmail());
            helper.setSubject("A&K CINEMA - XÁC NHẬN VÉ XEM PHIM #" + order.getId());

            // Định dạng tiền tệ VND
            NumberFormat vnFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            String totalAmount = vnFormat.format(order.getTotalAmount() * 1000); 

            // Lấy tên khách hàng từ firstName và lastName trong Entity User
            String customerName = order.getUser().getFirstName() + " " + order.getUser().getLastName();
            
            StringBuilder content = new StringBuilder();
            content.append("<div style='font-family: sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #eee; border-radius: 20px; overflow: hidden;'>");
            
            // Header màu đỏ đặc trưng rạp phim
            content.append("<div style='background-color: #dc2626; color: white; padding: 40px 20px; text-align: center;'>");
            content.append("<h1 style='margin: 0; font-size: 26px; letter-spacing: 2px;'>THANH TOÁN THÀNH CÔNG</h1>");
            content.append("<p style='margin-top: 10px; opacity: 0.9;'>Cảm ơn quý khách đã tin tưởng A&K Cinema</p>");
            content.append("</div>");

            // Body
            content.append("<div style='padding: 30px; line-height: 1.6;'>");
            content.append("<p>Xin chào <b>").append(customerName).append("</b>,</p>");
            content.append("<p>Giao dịch của quý khách đã được xác nhận. Vui lòng kiểm tra thông tin vé bên dưới:</p>");

            content.append("<div style='background-color: #fcfcfc; border: 1px dashed #ddd; border-radius: 15px; padding: 20px; margin: 20px 0;'>");
            content.append("<table style='width: 100%;'>");
            
            for (OrderDetail detail : order.getOrderDetails()) {
                content.append("<tr>");
                content.append("<td style='padding: 8px 0; color: #555;'>")
                       .append("<b>").append(detail.getItemType()).append("</b> (x").append(detail.getQuantity()).append(")</td>");
                content.append("<td style='padding: 8px 0; text-align: right; font-weight: bold;'>")
                       .append(vnFormat.format(detail.getPrice())).append("</td>");
                content.append("</tr>");
            }
            
            content.append("</table>");
            content.append("<div style='margin-top: 15px; padding-top: 15px; border-top: 1px solid #eee; text-align: right;'>");
            content.append("<span style='color: #888;'>Tổng cộng:</span>");
            content.append("<h2 style='margin: 0; color: #dc2626;'>").append(totalAmount).append("</h2>");
            content.append("</div>");
            content.append("</div>");

            content.append("<p style='font-size: 13px; color: #777; font-style: italic;'>* Quý khách vui lòng xuất trình email này tại quầy để nhận vé cứng và combo đã đặt.</p>");
            content.append("</div>");
            content.append("</div>");

            helper.setText(content.toString(), true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail đơn hàng " + order.getId() + ": " + e.getMessage());
        }
    }
}