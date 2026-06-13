package com.example.cinema.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Mở đường hầm /ws và cho phép các tên miền của ông truy cập
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000", "http://127.0.0.1:3000", "https://akcinema.vercel.app")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app"); // Tiền tố khi Client gửi lên Server
        registry.enableSimpleBroker("/topic", "/queue");    // Tiền tố khi Server trả về Client
    }
}