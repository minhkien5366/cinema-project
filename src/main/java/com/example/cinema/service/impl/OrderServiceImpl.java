package com.example.cinema.service.impl;

import com.example.cinema.dto.*;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.MailService;
import com.example.cinema.service.OrderService;
import com.example.cinema.service.VoucherService;
import com.example.cinema.service.SeatService;
import com.example.cinema.util.VNPayUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    // Thêm vào danh sách các repository ở đầu file
    private final PaymentRepository paymentRepository;
    private final VoucherService voucherService;
    private final SeatPriceConfigRepository seatPriceConfigRepository; 
    
    private final MailService mailService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SeatService seatService;

    @Value("${vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnp_PayUrl;
    @Value("${vnpay.tmnCode:2QXUI6Q7}")
    private String vnp_TmnCode;
    @Value("${vnpay.hashSecret:QTZTTGZMCYALZMMYVOTZMMZLXUKYVMLM}")
    private String vnp_HashSecret;
    @Value("${vnpay.returnUrl:http://localhost:8080/api/v1/orders/vnpay-callback}")
    private String vnp_ReturnUrl;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        User user = getCurrentUser();
        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));

        if (showtime.getStartTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Suất chiếu này đã diễn ra, không thể đặt vé!");
        }

        if (request.getSeatIds() != null && request.getSeatIds().size() > 6) {
            throw new RuntimeException("Mỗi giao dịch chỉ được đặt tối đa 6 ghế!");
        }

        if (request.getSeatIds() != null && !request.getSeatIds().isEmpty()) {
            List<Seat> allSeats = seatRepository.findByRoomId(showtime.getRoom().getId());
            List<Ticket> tickets = ticketRepository.findByShowtimeId(request.getShowtimeId());

            Set<Long> alreadyOccupied = tickets.stream()
                    .filter(t -> !"CANCELLED".equalsIgnoreCase(t.getStatus()))
                    .filter(t -> t.getSeat() != null)
                    .map(t -> t.getSeat().getId())
                    .collect(Collectors.toSet());

            Set<Long> newlySelected = new HashSet<>(request.getSeatIds());
            Map<String, List<Seat>> seatsByRow = allSeats.stream().collect(Collectors.groupingBy(Seat::getSeatRow));

            for (Map.Entry<String, List<Seat>> entry : seatsByRow.entrySet()) {
                List<Seat> rowSeats = entry.getValue();
                Map<Integer, Seat> seatMapByNum = new HashMap<>();
                for (Seat s : rowSeats) {
                    try { seatMapByNum.put(Integer.parseInt(s.getSeatNumber()), s); } catch (NumberFormatException e) {}
                }

                for (Seat currentSeat : rowSeats) {
                    String seatType = currentSeat.getSeatType() != null ? currentSeat.getSeatType().toUpperCase() : "NORMAL";
                    if ("SWEETBOX".equals(seatType) || "COUPLE".equals(seatType)) {
                        continue;
                    }

                    Long currentId = currentSeat.getId();
                    if (!alreadyOccupied.contains(currentId) && !newlySelected.contains(currentId)) {
                        int currentNum = Integer.parseInt(currentSeat.getSeatNumber());

                        Seat leftSeat = seatMapByNum.get(currentNum - 1);
                        boolean leftIsWallOrWalkway = (leftSeat == null);
                        boolean leftBlockedBySelectionOrOrder = false;
                        boolean leftSelectedByMe = false;

                        if (!leftIsWallOrWalkway) {
                            boolean leftOccupied = alreadyOccupied.contains(leftSeat.getId());
                            boolean leftSimSelected = newlySelected.contains(leftSeat.getId());
                            if (leftOccupied || leftSimSelected) {
                                leftBlockedBySelectionOrOrder = true;
                                if (leftSimSelected) leftSelectedByMe = true;
                            }
                        }

                        Seat rightSeat = seatMapByNum.get(currentNum + 1);
                        boolean rightIsWallOrWalkway = (rightSeat == null);
                        boolean rightBlockedBySelectionOrOrder = false;
                        boolean rightSelectedByMe = false;

                        if (!rightIsWallOrWalkway) {
                            boolean rightOccupied = alreadyOccupied.contains(rightSeat.getId());
                            boolean rightSimSelected = newlySelected.contains(rightSeat.getId());
                            if (rightOccupied || rightSimSelected) {
                                rightBlockedBySelectionOrOrder = true;
                                if (rightSimSelected) rightSelectedByMe = true;
                            }
                        }

                        boolean isSingleSeatError = false;

                        if (!leftIsWallOrWalkway && !rightIsWallOrWalkway && leftBlockedBySelectionOrOrder && rightBlockedBySelectionOrOrder) {
                            if (leftSelectedByMe || rightSelectedByMe) isSingleSeatError = true;
                        }
                        else if (leftIsWallOrWalkway && rightBlockedBySelectionOrOrder && rightSelectedByMe) {
                            isSingleSeatError = true;
                        }
                        else if (rightIsWallOrWalkway && leftBlockedBySelectionOrOrder && leftSelectedByMe) {
                            isSingleSeatError = true;
                        }

                        if (isSingleSeatError) {
                            throw new RuntimeException("Không được để lại ghế trống đơn lẻ thường (" + currentSeat.getName() + ") ở giữa hoặc đầu/cuối hàng ghế!");
                        }
                    }
                }
            }
        }

        int javaDay = showtime.getStartTime().getDayOfWeek().getValue();
        int dayValue = (javaDay == 7) ? 8 : javaDay + 1;

        Order order = new Order();
        order.setUser(user);
        order.setCinemaItem(showtime.getCinemaItem());
        order.setStatus("PENDING");
        order.setPaymentMethod(request.getPaymentMethod());
        
        Order savedOrder = orderRepository.save(order);
        double total = 0.0;
        List<OrderDetail> details = new ArrayList<>();

        String commonBookingCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (request.getSeatIds() != null) {
            for (Long seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId).orElseThrow(() -> new ResourceNotFoundException("Ghế không tồn tại"));
                
                if (ticketRepository.existsBySeatAndShowtimeAndStatusNotIgnoreCase(seat, showtime, "CANCELLED")) {
                    throw new RuntimeException("Ghế " + seat.getName() + " đã có người đặt!");
                }

                Double dynamicPrice = seatPriceConfigRepository
                    .findBySeatTypeAndDayOfWeek(seat.getSeatType().toUpperCase(), dayValue)
                    .map(SeatPriceConfig::getPrice)
                    .orElse(seat.getPrice());

                Ticket ticket = Ticket.builder()
                    .seat(seat)
                    .showtime(showtime)
                    .user(user)
                    .price(dynamicPrice)
                    .status("BOOKED") 
                    .bookingCode(commonBookingCode) 
                    .seatRow(seat.getSeatRow())       
                    .seatNumber(seat.getSeatNumber()) 
                    .seatName(seat.getName())         
                    .build();
                ticketRepository.save(ticket);

                OrderDetail d = new OrderDetail();
                d.setOrder(savedOrder); d.setItemType("TICKET"); d.setItemId(seatId); d.setQuantity(1); d.setPrice(dynamicPrice);
                details.add(d);
                total += dynamicPrice;
            }
        }

        if (request.getCombos() != null) {
            for (OrderRequest.ComboOrderDTO cReq : request.getCombos()) {
                Combo combo = comboRepository.findById(cReq.getComboId()).orElseThrow(() -> new ResourceNotFoundException("Combo không tồn tại"));
                OrderDetail d = new OrderDetail();
                d.setOrder(savedOrder); d.setItemType("COMBO"); d.setItemId(cReq.getComboId()); d.setQuantity(cReq.getQuantity()); d.setPrice(combo.getPrice());
                details.add(d);
                total += (combo.getPrice() * cReq.getQuantity());
            }
        }

        if (request.getVoucherCode() != null && !request.getVoucherCode().trim().isEmpty()) {
            Voucher voucher = voucherService.validateAndGetVoucher(request.getVoucherCode(), showtime.getCinemaItem().getId(), total);
            total = Math.max(0.0, total - voucher.getDiscountValue());
            voucher.setUsedCount(voucher.getUsedCount() + 1);
            voucherRepository.save(voucher);
        }

        savedOrder.setTotalAmount(total);
        orderDetailRepository.saveAll(details);
        savedOrder.setOrderDetails(details);
        
        OrderResponse response = mapToResponse(orderRepository.save(savedOrder));

        if ("VNPAY".equalsIgnoreCase(request.getPaymentMethod())) {
            response.setPaymentUrl(generateVNPayUrl(savedOrder));
        }

        return response;
    }

    private String generateVNPayUrl(Order order) {
        long amount = (long) (order.getTotalAmount() * 100);
        Map<String, String> vnp_Params = new TreeMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", String.valueOf(order.getId()));
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang: " + order.getId());
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", "127.0.0.1");

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        vnp_Params.put("vnp_CreateDate", now.format(formatter));

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<Map.Entry<String, String>> itr = vnp_Params.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, String> entry = itr.next();
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        
        String queryUrl = query.toString();
        String vnp_SecureHash = VNPayUtils.hmacSHA512(vnp_HashSecret, hashData.toString());
        
        return vnp_PayUrl + "?" + queryUrl + "&vnp_SecureHash=" + vnp_SecureHash;
    }

  @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Đơn hàng không tồn tại"));
        
        String status = newStatus.toUpperCase();
        order.setStatus(status);

        Long showtimeId = orderRepository.findShowtimeIdByOrderId(order.getId());
        final Long finalShowtimeId = showtimeId; 

        if (order.getOrderDetails() != null && finalShowtimeId != null) {
            for (OrderDetail d : order.getOrderDetails()) {
                if ("TICKET".equals(d.getItemType())) {
                    List<Ticket> tickets = ticketRepository.findBySeatIdAndShowtimeId(d.getItemId(), finalShowtimeId);
                    
                    // 🎯 TỐI ƯU CẬP NHẬT: Lấy vé mới nhất (max ID) thuộc User này, cô lập hoàn toàn các vé CANCELLED cũ.
                    Optional<Ticket> latestTicket = tickets.stream()
                            .filter(t -> t.getUser() != null && t.getUser().getUserId().equals(order.getUser().getUserId()))
                            .max(Comparator.comparing(Ticket::getId));

                    if (latestTicket.isPresent()) {
                        Ticket t = latestTicket.get();
                        if ("PAID".equals(status)) {
                            t.setStatus("PAID");
                        } else if ("CANCELLED".equals(status)) {
                            t.setStatus("CANCELLED"); 
                        } else if ("USED".equals(status)) {
                            t.setStatus("USED");
                        }
                        ticketRepository.save(t);
                    }
                }
            }
        }
        
        Order savedOrder = orderRepository.save(order);
        
        // 🎯 CẬP NHẬT TRẠNG THÁI THANH TOÁN VÀO BẢNG PAYMENT (LỖI ÔNG CẦN FIX)
        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(savedOrder.getId());
        Payment payment = paymentOpt.orElse(new Payment());
        payment.setOrder(savedOrder);
        payment.setAmount(savedOrder.getTotalAmount());
        payment.setStatus(status);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment); // Lưu trạng thái thanh toán vào DB
        
        if ("PAID".equals(status) || "USED".equals(status)) {
            if ("PAID".equals(status)) {
                mailService.sendOrderConfirmation(savedOrder);
            }

            try {
                Map<String, Object> adminNotify = new HashMap<>();
                adminNotify.put("message", "Cập nhật trạng thái đơn hàng!");
                adminNotify.put("orderId", savedOrder.getId());
                adminNotify.put("customer", savedOrder.getUser().getFirstName() + " " + savedOrder.getUser().getLastName());
                adminNotify.put("total", savedOrder.getTotalAmount());
                adminNotify.put("status", status);
                
                messagingTemplate.convertAndSend("/topic/admin-notifications", adminNotify, new HashMap<>());
            } catch (Exception e) {
                System.err.println("Thông báo ngầm: Hệ thống WebSocket Realtime local tạm thời chưa bắt được Client: " + e.getMessage());
            }
        }

        return mapToResponse(savedOrder);
    }
    @Override
    public OrderResponse scanOrderTicket(String bookingCode) {
        User staff = getCurrentUser();

        List<Ticket> tickets = ticketRepository.findAll().stream()
                .filter(t -> bookingCode.equalsIgnoreCase(t.getBookingCode()))
                .collect(Collectors.toList());

        if (tickets.isEmpty()) {
            throw new ResourceNotFoundException("Mã đặt vé '" + bookingCode + "' không tồn tại trên hệ thống!");
        }

        Ticket sampleTicket = tickets.get(0);
        Seat seat = sampleTicket.getSeat();
        if (seat == null) {
            throw new RuntimeException("Dữ liệu vật lý ghế ngồi dính kèm mã đặt vé bị lỗi!");
        }

        List<OrderDetail> matchingDetails = orderDetailRepository.findAll().stream()
                .filter(d -> "TICKET".equals(d.getItemType()) && seat.getId().equals(d.getItemId()))
                .collect(Collectors.toList());

        Order order = null;
        for (OrderDetail od : matchingDetails) {
            if (od.getOrder() != null && od.getOrder().getUser().getUserId().equals(sampleTicket.getUser().getUserId())) {
                order = od.getOrder();
                break;
            }
        }

        if (order == null) {
            throw new ResourceNotFoundException("Không tìm thấy hóa đơn gốc đi kèm mã đặt vé này!");
        }

        if (isSuperAdmin(staff)) {
            throw new RuntimeException("Tài khoản Super Admin không có quyền soát vé trực tiếp tại quầy!");
        }

        if (staff.getManagedCinemaItemId() == null || !staff.getManagedCinemaItemId().equals(order.getCinemaItem().getId())) {
            throw new RuntimeException("Xâm nhập sai chi nhánh! Vé này thuộc cụm rạp '" + order.getCinemaItem().getName() + "'.");
        }

        String currentStatus = order.getStatus().toUpperCase();
        switch (currentStatus) {
            case "USED":
                throw new RuntimeException("CẢNH BÁO GIAN LẬN: Mã vé " + bookingCode + " này đã được soát trước đó!");
            case "CANCELLED":
                throw new RuntimeException("Vé lỗi: Giao dịch này đã bị hủy bỏ hoặc hoàn tiền!");
            case "PENDING":
                throw new RuntimeException("Vé chưa kích hoạt: Đơn hàng vẫn đang ở trạng thái chờ thanh toán!");
            case "PAID":
                break; 
            default:
                throw new RuntimeException("Trạng thái vé không hợp lệ để soát tại quầy: " + currentStatus);
        }

        if (sampleTicket.getShowtime() != null) {
            if (sampleTicket.getShowtime().getStartTime().plusHours(2).isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Vé hết hạn! Suất chiếu của phim này đã kết thúc.");
            }
        }

        return mapToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse confirmCheckIn(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Đơn hàng không tồn tại"));
        
        String foundBookingCode = "N/A";
        if (order.getOrderDetails() != null) {
            for (OrderDetail d : order.getOrderDetails()) {
                if ("TICKET".equals(d.getItemType())) {
                    List<Ticket> tickets = ticketRepository.findBySeatIdAndShowtimeId(d.getItemId(), orderRepository.findShowtimeIdByOrderId(order.getId()));
                    
                    // 🎯 TỐI ƯU CHECKIN: Lấy mã bookingCode của vé mới nhất.
                   // Thay logic trong vòng lặp của hàm confirmCheckIn bằng đoạn này:
                    Optional<Ticket> correctTicket = ticketRepository.findBySeatIdAndShowtimeId(d.getItemId(), orderRepository.findShowtimeIdByOrderId(order.getId()))
                            .stream()
                            .filter(t -> t.getUser() != null && t.getUser().getUserId().equals(order.getUser().getUserId()))
                            .max(Comparator.comparing(Ticket::getId)); // Chỉ lấy vé mới nhất

                    if (correctTicket.isPresent()) {
                        foundBookingCode = correctTicket.get().getBookingCode();
                        break;
                    }
                }
            }
        }
        
        scanOrderTicket(foundBookingCode);
        return updateOrderStatus(orderId, "USED");
    }

    @Override 
    public List<OrderResponse> getAllOrders() { 
        User user = getCurrentUser();
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        
        if (isSuperAdmin(user)) {
            return orderRepository.findAll(sort).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        
        if (user.getManagedCinemaItemId() == null) {
            return orderRepository.findAll(sort).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        
        return orderRepository.findByCinemaItem_Id(user.getManagedCinemaItemId(), sort).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override public List<OrderResponse> getMyOrders() { 
        return orderRepository.findByUserEmail(getCurrentUser().getEmail(), Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::mapToResponse).collect(Collectors.toList()); 
    }

    @Override public OrderResponse getOrderById(Long id) { 
        return mapToResponse(orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không thấy đơn hàng"))); 
    }

    private User getCurrentUser() { 
        String email = SecurityContextHolder.getContext().getAuthentication().getName(); 
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập hết hạn")); 
    }

    private boolean isSuperAdmin(User user) { 
        return user.getRoles().stream().anyMatch(r -> r.getRoleName().equalsIgnoreCase("SUPER_ADMIN")); 
    }

    private void validateAdminAccess(Long cinemaId) { 
        User user = getCurrentUser(); 
        if (isSuperAdmin(user)) return; 
        if (user.getManagedCinemaItemId() == null || !user.getManagedCinemaItemId().equals(cinemaId)) throw new RuntimeException("Không có quyền!"); 
    }

    private OrderResponse mapToResponse(Order order) {
        Long showtimeId = orderRepository.findShowtimeIdByOrderId(order.getId());
        String realBookingCode = null; 

        if (order.getOrderDetails() != null) {
            for (OrderDetail od : order.getOrderDetails()) {
                if ("TICKET".equals(od.getItemType()) && od.getItemId() != null) {
                    List<Ticket> dbTickets = ticketRepository.findBySeatIdAndShowtimeId(od.getItemId(), showtimeId);
                    
                    // 🎯 TỐI ƯU HIỂN THỊ: Lấy mã QR từ vé mới nhất, vứt bỏ toàn bộ vé CANCELLED cũ.
                  // Thay logic trong vòng lặp của hàm mapToResponse bằng đoạn này:
                    Optional<Ticket> myTicket = ticketRepository.findBySeatIdAndShowtimeId(od.getItemId(), showtimeId)
                            .stream()
                            .filter(t -> t.getUser() != null && t.getUser().getUserId().equals(order.getUser().getUserId()))
                            .max(Comparator.comparing(Ticket::getId)); // Chỉ lấy vé mới nhất

                    if (myTicket.isPresent()) {
                        realBookingCode = myTicket.get().getBookingCode();
                        break; 
                    }
                }
            }
        }

        String movieTitle = "Vé Xem Phim";
        String roomName = "N/A";
        String showDate = "Trong Ngày";
        String showTime = "N/A";

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        if (showtimeId != null) {
            Optional<Showtime> showtimeOpt = showtimeRepository.findById(showtimeId);
            if (showtimeOpt.isPresent()) {
                Showtime st = showtimeOpt.get();
                movieTitle = st.getMovie() != null ? st.getMovie().getTitle() : movieTitle;
                roomName = st.getRoom() != null ? st.getRoom().getName() : roomName;
                showDate = st.getStartTime().format(dateFormatter);
                showTime = st.getStartTime().format(timeFormatter) + " - " + st.getEndTime().format(timeFormatter);
            }
        }

        final Long finalShowtimeId = showtimeId;

        List<OrderDetailResponse> detailResponses = order.getOrderDetails().stream().map(d -> {
            String name = "";
            
            if ("TICKET".equals(d.getItemType())) {
                if (finalShowtimeId != null) {
                    List<Ticket> tickets = ticketRepository.findBySeatIdAndShowtimeId(d.getItemId(), finalShowtimeId);
                    
                    // 🎯 TỐI ƯU BILL INFO: In ra tên ghế của tấm vé mới nhất.
                    Optional<Ticket> myTicket = tickets.stream()
                            .filter(t -> t.getUser() != null && t.getUser().getUserId().equals(order.getUser().getUserId()))
                            .max(Comparator.comparing(Ticket::getId));

                    if (myTicket.isPresent()) {
                        Ticket t = myTicket.get();
                        name = "Ghế " + t.getSeatName() + " (Hàng " + t.getSeatRow() + " - Số " + t.getSeatNumber() + ")";
                    } else {
                        name = "Vé Xem Phim";
                    }
                } else {
                    name = "Vé Xem Phim";
                }
            } else {
                name = comboRepository.findById(d.getItemId())
                        .map(Combo::getName)
                        .orElse("Combo Bắp Nước");
            }

            return OrderDetailResponse.builder()
                    .id(d.getId())
                    .itemId(d.getItemId())
                    .itemType(d.getItemType())
                    .quantity(d.getQuantity())
                    .price(d.getPrice())
                    .itemName(name) 
                    .build();
        }).collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .createdAt(order.getCreatedAt())
                .cinemaItemId(order.getCinemaItem() != null ? order.getCinemaItem().getId() : null)
                .cinemaName(order.getCinemaItem() != null ? order.getCinemaItem().getName() : "N/A")
                .movieTitle(movieTitle)  
                .date(showDate)          
                .time(showTime)          
                .roomName(roomName)
                .bookingCode(realBookingCode) 
                .orderDetails(detailResponses) 
                .build();
    }
}