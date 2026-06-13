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
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

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

        // PHÂN LUỒNG: Nếu khách đang ép chat với AI (BOT)
        if ("BOT".equals(message.getReceiverRole())) {
            
            // Gọi AI
            String botReplyContent = aiChatService.getAiResponse(message.getContent());
            
            // Đóng gói tin nhắn của AI
            ChatMessageDto botReply = new ChatMessageDto(
                    message.getRoomId(),
                    "A&K AI",
                    botReplyContent,
                    "BOT",
                    "USER",
                    null // Sẽ được tự động gán giờ trong MemoryManager
            );
            
            // Lưu tin của AI vào RAM và đẩy ngược về phòng chat
            chatMemoryManager.saveMessage(botReply);
            messagingTemplate.convertAndSend("/topic/room/" + message.getRoomId(), botReply);
            
        } 
        // Nếu khách xin gặp người thật, gửi lệnh thông báo "Rung chuông" tới tất cả Admin/Owner
        else if ("ADMIN".equals(message.getReceiverRole())) {
            messagingTemplate.convertAndSend("/topic/admin.notifications", message);
        }
    }

    // API HTTP thường để Admin khi bấm vào một phòng chat nào đó sẽ lấy được 50 câu chat gần nhất
    @GetMapping("/api/v1/chat/history/{roomId}")
    @ResponseBody
    public List<ChatMessageDto> getChatHistory(@PathVariable String roomId) {
        return chatMemoryManager.getRoomHistory(roomId);
    }
}