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
        // 1. Kiểm tra ghế đã được đặt cho suất chiếu này chưa
        if (ticketRepository.existsBySeatIdAndShowtimeIdAndStatus(request.getSeatId(), request.getShowtimeId(), "BOOKED")) {
            throw new RuntimeException("Ghế này đã có người đặt cho suất chiếu này!");
        }

        // 2. Lấy thông tin thực thể
        Seat seat = seatRepository.findById(request.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));
        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));
        
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));

        // 3. Tạo vé
        Ticket ticket = new Ticket();
        ticket.setSeat(seat);
        ticket.setShowtime(showtime);
        ticket.setUser(user);
        ticket.setPrice(seat.getPrice()); // Lấy giá từ loại ghế
        ticket.setStatus("BOOKED");
        
        // Tạo mã đặt chỗ ngẫu nhiên 8 ký tự viết hoa
        ticket.setBookingCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        return ticketRepository.save(ticket);
    }

    @Override
    public List<Ticket> getMyTickets() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        return ticketRepository.findByUserUserId(user.getUserId());
    }

    @Override
    public Ticket getByBookingCode(String code) {
        Ticket ticket = ticketRepository.findByBookingCode(code);
        if (ticket == null) throw new ResourceNotFoundException("Không tìm thấy vé với mã này");
        return ticket;
    }

    @Override
    @Transactional
    public void cancelTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Vé không tồn tại"));
        ticket.setStatus("CANCELLED");
        ticketRepository.save(ticket);
    }
}