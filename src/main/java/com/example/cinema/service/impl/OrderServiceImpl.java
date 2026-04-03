package com.example.cinema.service.impl;

import com.example.cinema.dto.OrderRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;
    private final ComboRepository comboRepository;

    @Override
    @Transactional
    public Order createOrder(OrderRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));

        Order order = new Order();
        order.setUser(user);
        order.setStatus("PAID");
        order = orderRepository.save(order);

        double totalAmount = 0.0;
        List<OrderDetail> details = new ArrayList<>();

        // 1. Xử lý VÉ XEM PHIM
        if (request.getSeatIds() != null) {
            for (Long seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));
                
                OrderDetail detail = new OrderDetail();
                detail.setOrder(order);
                detail.setItemType("TICKET");
                detail.setItemId(seatId);
                detail.setQuantity(1);
                detail.setPrice(seat.getPrice());
                
                totalAmount += seat.getPrice();
                details.add(detail);
            }
        }

        // 2. Xử lý COMBO (Dùng OrderRequest.ComboOrderDTO để sửa lỗi hiển thị)
        if (request.getCombos() != null) {
            for (OrderRequest.ComboOrderDTO item : request.getCombos()) {
                Combo combo = comboRepository.findById(item.getComboId())
                        .orElseThrow(() -> new ResourceNotFoundException("Combo không tồn tại"));
                
                OrderDetail detail = new OrderDetail();
                detail.setOrder(order);
                detail.setItemType("COMBO");
                detail.setItemId(item.getComboId());
                detail.setQuantity(item.getQuantity());
                detail.setPrice(combo.getPrice());
                
                totalAmount += (combo.getPrice() * item.getQuantity());
                details.add(detail);
            }
        }

        order.setTotalAmount(totalAmount);
        orderDetailRepository.saveAll(details);
        
        return orderRepository.save(order);
    }

    // Các hàm getOrderById, getAllOrders... giữ nguyên như cũ
    @Override
    public Order getOrderById(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không thấy đơn hàng"));
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    public List<Order> getMyOrders() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return orderRepository.findAll().stream()
                .filter(o -> o.getUser().getEmail().equals(email))
                .toList();
    }
}