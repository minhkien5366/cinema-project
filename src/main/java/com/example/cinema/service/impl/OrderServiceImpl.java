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

import java.time.LocalDateTime;
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
    private final ShowtimeRepository showtimeRepository; 
    private final TicketRepository ticketRepository;     

    @Override
    @Transactional
    public Order createOrder(OrderRequest request) {
        // 1. KIỂM TRA ĐẦU VÀO (Chống lỗi null ID mà Ngọc Trần đang gặp)
        if (request.getShowtimeId() == null) {
            throw new IllegalArgumentException("Mã suất chiếu (showtimeId) không được để trống!");
        }

        // 2. Lấy thông tin người dùng đang đăng nhập từ Token
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập hết hạn hoặc người dùng không tồn tại"));

        // 3. Kiểm tra Suất chiếu
        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại với ID: " + request.getShowtimeId()));

        // 4. Khởi tạo đơn hàng (Order)
        Order order = new Order();
        order.setUser(user);
        order.setStatus("PENDING"); // Để là PENDING, khi nào khách chuyển khoản xong Admin mới xác nhận thành PAID
        order.setCreatedAt(LocalDateTime.now());
        order.setPaymentMethod(request.getPaymentMethod());
        
        // Lưu tạm để lấy ID cho các bước sau
        Order savedOrder = orderRepository.save(order);

        double calculatedTotal = 0.0;
        List<OrderDetail> details = new ArrayList<>();

        // 5. Xử lý VÉ XEM PHIM (TICKET)
        if (request.getSeatIds() != null && !request.getSeatIds().isEmpty()) {
            for (Long seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Ghế với ID " + seatId + " không tồn tại"));

                // KIỂM TRA TRÙNG GHẾ: Đảm bảo ghế chưa bị ai đặt trong suất chiếu này
                boolean isTaken = ticketRepository.existsBySeatAndShowtime(seat, showtime);
                if (isTaken) {
                    throw new RuntimeException("Ghế " + seat.getName() + " đã có người nhanh tay đặt trước rồi!");
                }

                // Lưu vào bảng Ticket (Đánh dấu trạng thái giữ chỗ tạm thời)
                Ticket ticket = new Ticket();
                ticket.setUser(user);
                ticket.setSeat(seat);
                ticket.setShowtime(showtime);
                ticket.setPrice(seat.getPrice());
                ticket.setStatus("PENDING"); // Chờ thanh toán
                ticket.setBookingCode("BK" + System.currentTimeMillis() + seatId);
                ticketRepository.save(ticket);

                // Tạo OrderDetail cho vé
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

        // 6. Xử lý COMBO (Bắp nước)
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

        // 7. Cập nhật tổng tiền thực tế vào Order
        savedOrder.setTotalAmount(calculatedTotal);
        orderDetailRepository.saveAll(details);
        
        return orderRepository.save(savedOrder);
    }

    @Override
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không thấy đơn hàng id: " + id));
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    public List<Order> getMyOrders() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        // Giả sử bạn đã có phương thức findByUserEmail trong OrderRepository
        // Nếu chưa có hãy dùng lọc Java Stream:
        return orderRepository.findAll().stream()
                .filter(o -> o.getUser().getEmail().equals(email))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .toList();
    }
}