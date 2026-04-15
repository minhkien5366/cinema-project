package com.example.cinema.service.impl;

import com.example.cinema.dto.*;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.OrderService;
import com.example.cinema.service.VoucherService; // Cần Import
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
    private final VoucherRepository voucherRepository; // Thêm Repository
    private final VoucherService voucherService;       // Thêm Service để dùng hàm validate

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        User user = getCurrentUser();
        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));

        // 1. Khởi tạo Order gắn với Chi nhánh của Suất chiếu
        Order order = new Order();
        order.setUser(user);
        order.setCinemaItem(showtime.getCinemaItem());
        order.setStatus("PENDING");
        order.setPaymentMethod(request.getPaymentMethod());
        
        Order savedOrder = orderRepository.save(order);
        double total = 0.0;
        List<OrderDetail> details = new ArrayList<>();

        // 2. Xử lý Vé
        if (request.getSeatIds() != null) {
            for (Long seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId).orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));
                
                if (ticketRepository.existsBySeatAndShowtime(seat, showtime)) {
                    throw new RuntimeException("Ghế " + seat.getName() + " đã có người đặt!");
                }

                Ticket ticket = new Ticket();
                ticket.setSeat(seat);
                ticket.setShowtime(showtime);
                ticket.setUser(user);
                ticket.setPrice(seat.getPrice());
                ticket.setStatus("BOOKED");
                ticket.setBookingCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                ticketRepository.save(ticket);

                OrderDetail d = new OrderDetail();
                d.setOrder(savedOrder);
                d.setItemType("TICKET");
                d.setItemId(seatId);
                d.setQuantity(1);
                d.setPrice(seat.getPrice());
                details.add(d);
                total += seat.getPrice();
            }
        }

        // 3. Xử lý Combo
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

        // --- MỚI: XỬ LÝ MÃ GIẢM GIÁ (VOUCHER) ---
        if (request.getVoucherCode() != null && !request.getVoucherCode().trim().isEmpty()) {
            // Kiểm tra tính hợp lệ của mã thông qua VoucherService
            Voucher voucher = voucherService.validateAndGetVoucher(
                request.getVoucherCode(), 
                showtime.getCinemaItem().getId(), 
                total
            );
            
            // Trừ tiền giảm giá (Đảm bảo không bị âm tiền)
            total = Math.max(0.0, total - voucher.getDiscountValue());
            
            // Cập nhật số lượt đã sử dụng của Voucher
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
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        validateAdminAccess(order.getCinemaItem().getId());

        String status = newStatus.toUpperCase();
        order.setStatus(status);

        // Đồng bộ Ticket status
        if (order.getOrderDetails() != null) {
            for (OrderDetail d : order.getOrderDetails()) {
                if ("TICKET".equals(d.getItemType())) {
                    // Cải tiến logic tìm Ticket: Dựa trên seat, showtime và user của đơn hàng
                    ticketRepository.findBySeatAndShowtimeAndUser(
                        seatRepository.findById(d.getItemId()).get(),
                        showtimeRepository.findById(order.getOrderDetails().get(0).getItemId()).get(), // Lấy showtime gốc (giả định)
                        order.getUser()
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
        
        if (user.getRoles().stream().anyMatch(r -> r.getRoleName().equals("SUPER_ADMIN"))) {
            return orderRepository.findAll(sort).stream().map(this::mapToResponse).collect(Collectors.toList());
        }
        
        return orderRepository.findByCinemaItem_Id(user.getManagedCinemaItemId(), sort)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getMyOrders() {
        return orderRepository.findByUserEmail(getCurrentUser().getEmail(), Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public OrderResponse getOrderById(Long id) {
        return mapToResponse(orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không thấy Order")));
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập hết hạn"));
    }

    private void validateAdminAccess(Long cinemaId) {
        User user = getCurrentUser();
        if (user.getRoles().stream().anyMatch(r -> r.getRoleName().equals("SUPER_ADMIN"))) return;
        if (user.getManagedCinemaItemId() == null || !user.getManagedCinemaItemId().equals(cinemaId)) {
            throw new RuntimeException("Bạn không có quyền quản lý đơn hàng của rạp này!");
        }
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .createdAt(order.getCreatedAt())
                .cinemaItemId(order.getCinemaItem() != null ? order.getCinemaItem().getId() : null)
                .cinemaName(order.getCinemaItem() != null ? order.getCinemaItem().getName() : "N/A")
                .orderDetails(order.getOrderDetails().stream().map(d -> OrderDetailResponse.builder()
                        .id(d.getId()).itemId(d.getItemId()).itemType(d.getItemType())
                        .quantity(d.getQuantity()).price(d.getPrice()).build()).collect(Collectors.toList()))
                .build();
    }
}