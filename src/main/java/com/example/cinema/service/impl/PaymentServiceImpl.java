package com.example.cinema.service.impl;

import com.example.cinema.dto.PaymentResponse;
import com.example.cinema.entity.Order;
import com.example.cinema.entity.Payment;
import com.example.cinema.entity.User;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.OrderRepository;
import com.example.cinema.repository.PaymentRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    // ================= CREATE PAYMENT =================
    @Override
    @Transactional
    public PaymentResponse createPayment(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElse(new Payment());

        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus("PENDING");

        String qrData =
                "20081015|BANK_ID|ACCOUNT_NO|"
                        + order.getTotalAmount()
                        + "|ORDER"
                        + orderId;

        payment.setQrContent(qrData);

        paymentRepository.save(payment);

        return mapToResponse(payment);
    }

    // ================= CONFIRM PAYMENT =================
    @Override
    @Transactional
    public PaymentResponse confirmPayment(Long orderId) {

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Không tìm thấy thông tin thanh toán"));

        payment.setStatus("COMPLETED");
        payment.setPaidAt(LocalDateTime.now());

        Order order = payment.getOrder();

        // ✅ CHỐNG CỘNG ĐIỂM 2 LẦN
        if (!"PAID".equalsIgnoreCase(order.getStatus())) {

            order.setStatus("PAID");

            // ===== TÍCH ĐIỂM =====
            User user = order.getUser();

            int earnedPoints =
                    (int) (order.getTotalAmount() / 10000); // 10k = 1 điểm

            user.setPoints(user.getPoints() + earnedPoints);

            userRepository.save(user);
            // =====================
        }

        orderRepository.save(order);

        paymentRepository.save(payment);

        return mapToResponse(payment);
    }

    // ================= GET PAYMENT =================
    @Override
    public Payment getPaymentByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Thanh toán không tồn tại"));
    }

    // ================= MAPPER =================
    private PaymentResponse mapToResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .status(p.getStatus())
                .qrContent(p.getQrContent())
                .orderId(p.getOrder().getId())
                .createdAt(p.getCreatedAt())
                .build();
    }
}