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
    private final ShowtimeRepository showtimeRepository; // Cần bổ sung thêm Repository này
    private final TicketRepository ticketRepository;     // Cần bổ sung để lưu vết vé đã đặt

    @Override
    @Transactional
    public Order createOrder(OrderRequest request) {
        // 1. Lấy thông tin người dùng đang đăng nhập
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));

        // 2. Kiểm tra Suất chiếu có tồn tại không
        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));

        // 3. Tạo đơn hàng (Order)
        Order order = new Order();
        order.setUser(user);
        order.setStatus("PAID"); // Hoặc "PENDING" tùy quy trình thanh toán của bạn
        order.setCreatedAt(LocalDateTime.now());
        
        // Lưu nháp để lấy ID
        order = orderRepository.save(order);

        double totalAmount = 0.0;
        List<OrderDetail> details = new ArrayList<>();

        // 4. Xử lý VÉ XEM PHIM (TICKET)
        if (request.getSeatIds() != null) {
            for (Long seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));

                // --- KIỂM TRA TRÙNG GHẾ: Rất quan trọng ---
                boolean isTaken = ticketRepository.existsBySeatAndShowtime(seat, showtime);
                if (isTaken) {
                    throw new RuntimeException("Ghế " + seat.getName() + " đã được đặt rồi!");
                }

                // Lưu vào Ticket (để đánh dấu ghế đã có người ngồi trong Suất chiếu này)
                Ticket ticket = new Ticket();
                ticket.setUser(user);
                ticket.setSeat(seat);
                ticket.setShowtime(showtime);
                ticket.setPrice(seat.getPrice());
                ticket.setStatus("PAID");
                ticket.setBookingCode("BK" + System.currentTimeMillis() + seatId); // Tạo mã code ngẫu nhiên
                ticketRepository.save(ticket);

                // Tạo dòng OrderDetail tương ứng
                OrderDetail detail = new OrderDetail();
                detail.setOrder(order);
                detail.setItemType("TICKET");
                detail.setItemId(seatId); // Có thể lưu ticket.getId() nếu muốn
                detail.setQuantity(1);
                detail.setPrice(seat.getPrice());
                
                totalAmount += seat.getPrice();
                details.add(detail);
            }
        }

        // 5. Xử lý COMBO
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

        // 6. Cập nhật tổng tiền cuối cùng cho Order
        order.setTotalAmount(totalAmount);
        orderDetailRepository.saveAll(details);
        
        return orderRepository.save(order);
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
        // Tối ưu: Nên viết hàm findByUserEmail trong OrderRepository
        // Hiện tại dùng tạm cách này để lọc:
        return orderRepository.findAll().stream()
                .filter(o -> o.getUser().getEmail().equals(email))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // Hiện đơn mới nhất lên đầu
                .toList();
    }
}