package com.example.cinema.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ChatMessageDto {
    private String roomId;      // ID phòng (ví dụ: Tên khách hàng hoặc Session ID)
    private String sender;      // Tên người gửi ("Khách Hàng", "A&K AI", "Admin")
    private String content;     // Nội dung tin nhắn
    private String senderRole;  // Vai trò người gửi: "USER", "BOT", "ADMIN"
    private String receiverRole;// Vai trò người nhận: "BOT", "ADMIN"
    private String timestamp;   // Giờ gửi tin
}