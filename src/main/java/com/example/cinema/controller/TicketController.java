package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.TicketRequest;
import com.example.cinema.entity.Ticket;
import com.example.cinema.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Ticket>> createTicket(@RequestBody TicketRequest request) {
        return ResponseEntity.ok(ApiResponse.<Ticket>builder()
                .status(201)
                .message("Đặt vé thành công")
                .data(ticketService.createTicket(request))
                .build());
    }

    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Ticket>>> getMyHistory() {
        return ResponseEntity.ok(ApiResponse.<List<Ticket>>builder()
                .status(200)
                .message("Lấy lịch sử vé thành công")
                .data(ticketService.getMyTickets())
                .build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<Ticket>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.<Ticket>builder()
                .status(200)
                .message("Thành công")
                .data(ticketService.getByBookingCode(code))
                .build());
    }
}