package com.example.cinema.service.impl;

import com.example.cinema.dto.*;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;
    private final ComboRepository comboRepository;
    private final ShowtimeRepository showtimeRepository; 
    private final TicketRepository ticketRepository;     

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        if (request.getShowtimeId() == null) {
            throw new IllegalArgumentException("Mã suất chiếu (showtimeId) không được để trống!");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập hết hạn"));

        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));

        Order order = new Order();
        order.setUser(user);
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        order.setPaymentMethod(request.getPaymentMethod());
        
        Order savedOrder = orderRepository.save(order);
        double calculatedTotal = 0.0;
        List<OrderDetail> details = new ArrayList<>();

        if (request.getSeatIds() != null && !request.getSeatIds().isEmpty()) {
            for (Long seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));

                if (ticketRepository.existsBySeatAndShowtime(seat, showtime)) {
                    throw new RuntimeException("Ghế " + seat.getName() + " đã bị người khác đặt!");
                }

                Ticket ticket = new Ticket();
                ticket.setUser(user);
                ticket.setSeat(seat);
                ticket.setShowtime(showtime);
                ticket.setPrice(seat.getPrice());
                ticket.setStatus("PENDING");
                ticket.setBookingCode("BK" + System.currentTimeMillis() + seatId);
                ticketRepository.save(ticket);

                OrderDetail detail = new OrderDetail();
                detail.setOrder(savedOrder);
                detail.setItemType("TICKET");
                detail.setItemId(seatId); 
                detail.setQuantity(1);
                detail.setPrice(seat.getPrice());
                
                calculatedTotal += seat.getPrice();
                details.add(detail);
            }
        }

        if (request.getCombos() != null) {
            for (OrderRequest.ComboOrderDTO item : request.getCombos()) {
                Combo combo = comboRepository.findById(item.getComboId())
                        .orElseThrow(() -> new ResourceNotFoundException("Combo không tồn tại"));
                
                OrderDetail detail = new OrderDetail();
                detail.setOrder(savedOrder);
                detail.setItemType("COMBO");
                detail.setItemId(item.getComboId());
                detail.setQuantity(item.getQuantity());
                detail.setPrice(combo.getPrice());
                
                calculatedTotal += (combo.getPrice() * item.getQuantity());
                details.add(detail);
            }
        }

        savedOrder.setTotalAmount(calculatedTotal);
        orderDetailRepository.saveAll(details);
        savedOrder.setOrderDetails(details);
        
        return mapToResponse(orderRepository.save(savedOrder));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng id: " + orderId));

        String upperStatus = newStatus.toUpperCase();
        order.setStatus(upperStatus);

        if (order.getOrderDetails() != null) {
            for (OrderDetail detail : order.getOrderDetails()) {
                if ("TICKET".equals(detail.getItemType())) {
                    List<Ticket> tickets = ticketRepository.findByUserUserId(order.getUser().getUserId());
                    
                    tickets.stream()
                        // FIX TẠI ĐÂY: Dùng getId() thay vì getSeatId()
                        .filter(t -> t.getSeat().getId().equals(detail.getItemId()) 
                                  && ("PENDING".equals(t.getStatus()) || "BOOKED".equals(t.getStatus())))
                        .findFirst()
                        .ifPresent(t -> {
                            t.setStatus(upperStatus);
                            ticketRepository.save(t);
                        });
                }
            }
        }

        return mapToResponse(orderRepository.save(order));
    }

    @Override
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không thấy đơn hàng id: " + id));
        return mapToResponse(order);
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getMyOrders() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return orderRepository.findAll().stream()
                .filter(o -> o.getUser().getEmail().equals(email))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderDetailResponse> details = order.getOrderDetails().stream()
                .map(d -> OrderDetailResponse.builder()
                        .id(d.getId())
                        .itemId(d.getItemId())
                        .itemType(d.getItemType())
                        .quantity(d.getQuantity())
                        .price(d.getPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .createdAt(order.getCreatedAt())
                .orderDetails(details)
                .build();
    }
}