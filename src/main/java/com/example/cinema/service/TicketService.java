package com.example.cinema.service;

import com.example.cinema.dto.TicketRequest;
import com.example.cinema.entity.Ticket;
import java.util.List;

public interface TicketService {
    Ticket createTicket(TicketRequest request);
    List<Ticket> getMyTickets();
    Ticket getByBookingCode(String code);
    void cancelTicket(Long ticketId);
}