package com.example.cinema.service.impl;

import com.example.cinema.dto.TicketRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Ticket createTicket(TicketRequest request) {
        // 1. Lấy thông tin thực thể trước
        Seat seat = seatRepository.findById(request.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));
        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));
        
        // 2. Kiểm tra ghế đã bị chiếm chưa (Bao gồm cả trạng thái BOOKED và PAID)
        // Mình dùng danh sách trạng thái để kiểm tra cho chắc chắn
        List<String> busyStatuses = Arrays.asList("BOOKED", "PAID");
        if (ticketRepository.existsBySeatAndShowtimeAndStatusIn(seat, showtime, busyStatuses)) {
            throw new RuntimeException("Ghế " + seat.getSeatNumber() + " đã có người đặt hoặc đã thanh toán!");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));

        // 3. Tạo vé mới
        Ticket ticket = new Ticket();
        ticket.setSeat(seat);
        ticket.setShowtime(showtime);
        ticket.setUser(user);
        ticket.setPrice(seat.getPrice());
        ticket.setStatus("BOOKED");
        
        // Tạo mã đặt chỗ duy nhất (8 ký tự cuối của UUID)
        String bookingCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        ticket.setBookingCode(bookingCode);

        return ticketRepository.save(ticket);
    }

    @Override
    public List<Ticket> getMyTickets() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin cá nhân"));
        // Sử dụng hàm tìm kiếm theo Email đã thêm ở Repository sẽ nhanh hơn
        return ticketRepository.findByUserEmailOrderByCreatedAtDesc(email);
    }

    @Override
    public Ticket getByBookingCode(String code) {
        // Cập nhật dùng Optional để tránh NullPointerException
        return ticketRepository.findByBookingCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Mã đặt vé " + code + " không tồn tại trên hệ thống"));
    }

    @Override
    public List<Ticket> getTicketsByShowtime(Long showtimeId) {
        return ticketRepository.findByShowtimeId(showtimeId);
    }

    @Override
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    @Override
    @Transactional
    public void cancelTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé để hủy"));
        
        // Chỉ cho phép hủy nếu vé chưa thanh toán hoặc theo chính sách rạp
        if ("PAID".equals(ticket.getStatus())) {
            throw new RuntimeException("Vé đã thanh toán không thể tự hủy trên hệ thống!");
        }
        
        ticket.setStatus("CANCELLED");
        ticketRepository.save(ticket);
    }
}