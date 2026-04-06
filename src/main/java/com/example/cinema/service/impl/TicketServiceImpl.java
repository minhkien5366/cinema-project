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
import org.springframework.data.domain.Sort;

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
        Seat seat = seatRepository.findById(request.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));
        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));
        
        // Kiểm tra ghế đã bị chiếm chưa (Tránh đặt trùng)
        List<String> busyStatuses = Arrays.asList("BOOKED", "PAID", "OCCUPIED");
        if (ticketRepository.existsBySeatAndShowtimeAndStatusIn(seat, showtime, busyStatuses)) {
            throw new RuntimeException("Ghế " + seat.getSeatRow() + seat.getSeatNumber() + " đã có người đặt!");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Ticket ticket = new Ticket();
        ticket.setSeat(seat);
        ticket.setShowtime(showtime);
        ticket.setUser(user);
        ticket.setPrice(seat.getPrice());
        ticket.setStatus("BOOKED");
        
        // Tạo mã booking ngẫu nhiên 8 ký tự
        String bookingCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        ticket.setBookingCode(bookingCode);

        return ticketRepository.save(ticket);
    }

    @Override
    public List<Ticket> getMyTickets() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        // Giả sử bạn đã thêm hàm findByUserEmailOrderByCreatedAtDesc trong Repository
        return ticketRepository.findByUserEmailOrderByCreatedAtDesc(email);
    }

    @Override
    public Ticket getByBookingCode(String code) {
        return ticketRepository.findByBookingCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé với mã: " + code));
    }

    @Override
    public List<Ticket> getTicketsByShowtime(Long showtimeId) {
        return ticketRepository.findByShowtimeId(showtimeId);
    }

    @Override
    public List<Ticket> getAllTickets() {
        // Sắp xếp vé mới nhất lên đầu cho Admin dễ nhìn
        return ticketRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Override
    @Transactional
    public void cancelTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé"));
        
        // Logic: Không cho hủy vé đã thanh toán (tùy bạn chỉnh sửa)
        if ("PAID".equals(ticket.getStatus())) {
            throw new RuntimeException("Vé đã thanh toán. Vui lòng liên hệ quầy để hoàn tiền.");
        }
        
        ticket.setStatus("CANCELLED");
        ticketRepository.save(ticket);
    }
}