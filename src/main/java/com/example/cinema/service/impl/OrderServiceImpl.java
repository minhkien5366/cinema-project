package com.example.cinema.service.impl;

import com.example.cinema.dto.*;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.OrderService;
import com.example.cinema.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private final VoucherRepository voucherRepository;
    private final VoucherService voucherService;
    private final SeatPriceConfigRepository seatPriceConfigRepository; 

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        User user = getCurrentUser();
        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));

        // RÀNG BUỘC 1: Chặn đặt vé nếu suất chiếu đã bắt đầu hoặc kết thúc
        if (showtime.getStartTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Suất chiếu này đã diễn ra hoặc đã bắt đầu, không thể đặt vé!");
        }

        // TÍNH TOÁN THỨ TRONG TUẦN (Mapping: 2 = Thứ 2, ..., 8 = Chủ Nhật)
        int dayValue = showtime.getStartTime().getDayOfWeek().getValue() + 1;
        if (dayValue > 8) dayValue = 2; 

        Order order = new Order();
        order.setUser(user);
        order.setCinemaItem(showtime.getCinemaItem());
        order.setStatus("PENDING");
        order.setPaymentMethod(request.getPaymentMethod());
        
        Order savedOrder = orderRepository.save(order);
        double total = 0.0;
        List<OrderDetail> details = new ArrayList<>();

        // XỬ LÝ VÉ (TÍNH GIÁ ĐỘNG)
        if (request.getSeatIds() != null) {
            for (Long seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId).orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));
                
                if (ticketRepository.existsBySeatAndShowtime(seat, showtime)) {
                    throw new RuntimeException("Ghế " + seat.getName() + " đã có người đặt!");
                }

                // TRA CỨU GIÁ THEO THỨ + LOẠI GHẾ + RẠP
                Double dynamicPrice = seatPriceConfigRepository
                    .findBySeatTypeAndDayOfWeekAndCinemaItem_Id(
                        seat.getSeatType().toUpperCase(), 
                        dayValue, 
                        showtime.getCinemaItem().getId()
                    )
                    .map(SeatPriceConfig::getPrice)
                    .orElse(seat.getPrice()); 

                Ticket ticket = new Ticket();
                ticket.setSeat(seat);
                ticket.setShowtime(showtime);
                ticket.setUser(user);
                ticket.setPrice(dynamicPrice);
                ticket.setStatus("BOOKED");
                ticket.setBookingCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                ticketRepository.save(ticket);

                OrderDetail d = new OrderDetail();
                d.setOrder(savedOrder);
                d.setItemType("TICKET");
                d.setItemId(seatId);
                d.setQuantity(1);
                d.setPrice(dynamicPrice);
                details.add(d);
                total += dynamicPrice;
            }
        }

        // XỬ LÝ COMBO
        if (request.getCombos() != null) {
            for (OrderRequest.ComboOrderDTO cReq : request.getCombos()) {
                Combo combo = comboRepository.findById(cReq.getComboId()).orElseThrow(() -> new ResourceNotFoundException("Combo không tồn tại"));
                OrderDetail d = new OrderDetail();
                d.setOrder(savedOrder);
                d.setItemType("COMBO");
                d.setItemId(cReq.getComboId());
                d.setQuantity(cReq.getQuantity());
                d.setPrice(combo.getPrice());
                details.add(d);
                total += (combo.getPrice() * cReq.getQuantity());
            }
        }

        // XỬ LÝ VOUCHER
        if (request.getVoucherCode() != null && !request.getVoucherCode().trim().isEmpty()) {
            Voucher voucher = voucherService.validateAndGetVoucher(request.getVoucherCode(), showtime.getCinemaItem().getId(), total);
            total = Math.max(0.0, total - voucher.getDiscountValue());
            voucher.setUsedCount(voucher.getUsedCount() + 1);
            voucherRepository.save(voucher);
        }

        savedOrder.setTotalAmount(total);
        orderDetailRepository.saveAll(details);
        savedOrder.setOrderDetails(details);
        
        return mapToResponse(orderRepository.save(savedOrder));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Đơn hàng không tồn tại"));
        validateAdminAccess(order.getCinemaItem().getId());

        String status = newStatus.toUpperCase();
        order.setStatus(status);

        if (order.getOrderDetails() != null) {
            for (OrderDetail d : order.getOrderDetails()) {
                if ("TICKET".equals(d.getItemType())) {
                    ticketRepository.findBySeatIdAndShowtimeId(
                        d.getItemId(), 
                        orderRepository.findShowtimeIdByOrderId(order.getId())
                    ).ifPresent(t -> {
                        t.setStatus(status.equals("PAID") ? "PAID" : "CANCELLED");
                        ticketRepository.save(t);
                    });
                }
            }
        }
        return mapToResponse(orderRepository.save(order));
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        User user = getCurrentUser();
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (isSuperAdmin(user)) return orderRepository.findAll(sort).stream().map(this::mapToResponse).collect(Collectors.toList());
        return orderRepository.findByCinemaItem_Id(user.getManagedCinemaItemId(), sort).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getMyOrders() {
        return orderRepository.findByUserEmail(getCurrentUser().getEmail(), Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public OrderResponse getOrderById(Long id) {
        return mapToResponse(orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không thấy đơn hàng")));
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập hết hạn"));
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getRoleName().equalsIgnoreCase("SUPER_ADMIN") || r.getRoleName().equalsIgnoreCase("ROLE_SUPER_ADMIN"));
    }

    private void validateAdminAccess(Long cinemaId) {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return;
        if (user.getManagedCinemaItemId() == null || !user.getManagedCinemaItemId().equals(cinemaId)) {
            throw new RuntimeException("Bạn không có quyền quản lý đơn hàng của chi nhánh này!");
        }
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId()).status(order.getStatus()).totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod()).createdAt(order.getCreatedAt())
                .cinemaItemId(order.getCinemaItem() != null ? order.getCinemaItem().getId() : null)
                .cinemaName(order.getCinemaItem() != null ? order.getCinemaItem().getName() : "N/A")
                .orderDetails(order.getOrderDetails().stream().map(d -> OrderDetailResponse.builder()
                        .id(d.getId()).itemId(d.getItemId()).itemType(d.getItemType())
                        .quantity(d.getQuantity()).price(d.getPrice()).build()).collect(Collectors.toList()))
                .build();
    }
}