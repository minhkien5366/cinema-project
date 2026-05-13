package com.example.cinema.service;

import com.example.cinema.entity.Order;

public interface MailService {
    void sendOrderConfirmation(Order order);
}