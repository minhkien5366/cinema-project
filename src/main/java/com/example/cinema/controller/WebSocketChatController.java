package com.example.cinema.controller;

import com.example.cinema.dto.ChatMessageDto;
import com.example.cinema.service.impl.AiChatServiceImpl;
import com.example.cinema.service.impl.ChatMemoryManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class WebSocketChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMemoryManager chatMemoryManager;
    private final AiChatServiceImpl aiChatService;

    @MessageMapping("/chat.sendMessage")
    public void processMessage(@Payload ChatMessageDto message) {
        // Lưu tin nhắn của khách/Admin vào RAM
        chatMemoryManager.saveMessage(message);
        
        // Phát tin nhắn này vào phòng chat (Cả màn hình User và Admin nếu đang mở phòng này đều nhảy chữ)
        messagingTemplate.convertAndSend("/topic/room/" + message.getRoomId(), message);

        // PHÂN LUỒNG 1: Nếu khách đang ép chat với AI (BOT)
        if ("BOT".equals(message.getReceiverRole())) {
            
            // 🔥 TRUYỀN LỊCH SỬ CHAT VÀO CHO AI ĐỂ NÓ NHỚ NGỮ CẢNH
            List<ChatMessageDto> history = chatMemoryManager.getRoomHistory(message.getRoomId());
            
            // Gọi AI (Truyền cả tin nhắn hiện tại VÀ lịch sử)
            String botReplyContent = aiChatService.getAiResponse(message.getContent(), history);
            
            // Đóng gói tin nhắn của AI
            ChatMessageDto botReply = new ChatMessageDto(
                    message.getRoomId(),
                    "A&K AI",
                    botReplyContent,
                    "BOT",
                    "USER",
                    null
            );
            
            // Lưu tin của AI vào RAM và đẩy ngược về phòng chat
            chatMemoryManager.saveMessage(botReply);
            messagingTemplate.convertAndSend("/topic/room/" + message.getRoomId(), botReply);
            
        } 
        // 🔥 PHÂN LUỒNG 2: Định tuyến gặp người thật theo đúng Chi nhánh
        else if ("ADMIN".equals(message.getReceiverRole())) {
            if (message.getCinemaItemId() != null) {
                // Bắn thông báo đích danh tới Admin của chi nhánh đó
                messagingTemplate.convertAndSend("/topic/admin.notifications.cinema." + message.getCinemaItemId(), message);
            } else {
                // Dự phòng: Nếu tin nhắn lỗi không có mã rạp, đẩy vào kênh hỗ trợ tổng
                messagingTemplate.convertAndSend("/topic/admin.notifications.general", message);
            }
        }
    }

    // API HTTP thường để Admin khi bấm vào một phòng chat nào đó sẽ lấy được 50 câu chat gần nhất
    @GetMapping("/api/v1/chat/history/{roomId}")
    @ResponseBody
    public List<ChatMessageDto> getChatHistory(@PathVariable String roomId) {
        return chatMemoryManager.getRoomHistory(roomId);
    }

    // API MỚI 1: Lấy danh sách các phòng chat đang mở của Rạp (Dành cho Admin khi F5)
    @GetMapping("/api/v1/chat/active-rooms/{cinemaItemId}")
    @ResponseBody
    public List<String> getActiveRoomsByCinema(@PathVariable Long cinemaItemId) {
        return chatMemoryManager.getAllRooms().stream()
            .filter(roomId -> {
                List<ChatMessageDto> history = chatMemoryManager.getRoomHistory(roomId);
                if (history.isEmpty()) return false;
                
                // Phòng này phải có chứa tin nhắn gửi đến chi nhánh đang tìm
                boolean matchCinema = history.stream()
                    .anyMatch(m -> cinemaItemId.equals(m.getCinemaItemId()));
                
                // Phòng này chưa bị kết thúc (Chưa có tin nhắn [SYSTEM_CLOSE])
                boolean isClosed = history.stream()
                    .anyMatch(m -> "[SYSTEM_CLOSE]".equals(m.getContent()));
                
                return matchCinema && !isClosed;
            })
            .collect(Collectors.toList());
    }

    // API MỚI 2: Đóng / Hủy cuộc trò chuyện
    @PostMapping("/api/v1/chat/close/{roomId}")
    @ResponseBody
    public String closeChatRoom(@PathVariable String roomId) {
        ChatMessageDto closeNotice = new ChatMessageDto(
            roomId,
            "HỆ THỐNG",
            "[SYSTEM_CLOSE]",
            "BOT",
            "USER",
            null
        );
        chatMemoryManager.saveMessage(closeNotice);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, closeNotice);
        return "SUCCESS";
    }
}