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

    private final CinemaComboRepository cinemaComboRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;
    private final ComboRepository comboRepository;
    private final ShowtimeRepository showtimeRepository; 
    private final TicketRepository ticketRepository;     
    private final VoucherRepository voucherRepository;
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
                Combo combo = comboRepository.findById(cReq.getComboId())
                        .orElseThrow(() -> new ResourceNotFoundException("Combo không tồn tại"));
                
                CinemaCombo cinemaCombo = cinemaComboRepository
                        .findByCinemaItemIdAndComboId(showtime.getCinemaItem().getId(), combo.getId())
                        .orElseThrow(() -> new RuntimeException("Combo " + combo.getName() + " hiện không bán tại rạp này!"));

                if (!cinemaCombo.isActive()) {
                    throw new RuntimeException("Combo " + combo.getName() + " đang tạm ngưng phục vụ tại rạp này!");
                }

                if (cinemaCombo.getStock() == null || cinemaCombo.getStock() < cReq.getQuantity()) {
                    throw new RuntimeException("Combo " + combo.getName() + " không đủ số lượng trong kho của rạp!");
                }

                cinemaCombo.setStock(cinemaCombo.getStock() - cReq.getQuantity());
                cinemaComboRepository.save(cinemaCombo);

                OrderDetail d = new OrderDetail();
                d.setOrder(savedOrder);
                d.setItemType("COMBO");
                d.setItemId(combo.getId());
                d.setItemName(combo.getName());
                d.setQuantity(cReq.getQuantity());
                d.setPrice(combo.getPrice());                
                details.add(d);
                total += (combo.getPrice() * cReq.getQuantity());
            }
        }

        // 🔥 LƯU TRỮ VOUCHER DƯỚI DẠNG MỘT MÓN HÀNG ẢO
        if (request.getVoucherCode() != null && !request.getVoucherCode().trim().isEmpty()) {
            Voucher voucher = voucherService.validateAndGetVoucher(request.getVoucherCode(), showtime.getCinemaItem().getId(), total);
            total = Math.max(0.0, total - voucher.getDiscountValue());
            
            OrderDetail dVoucher = new OrderDetail();
            dVoucher.setOrder(savedOrder);
            dVoucher.setItemType("VOUCHER");
            dVoucher.setItemId(voucher.getId());
            dVoucher.setItemName(voucher.getCode());
            dVoucher.setQuantity(1);
            dVoucher.setPrice(0.0); // Không cộng thêm tiền vào đơn
            details.add(dVoucher);
        }

        // 🎯 FIX CHÍ MẠNG: Lưu lại tổng tiền ĐÃ GIẢM vào savedOrder TRƯỚC khi gọi sinh link VNPay
        savedOrder.setTotalAmount(total);
        orderDetailRepository.saveAll(details);
        savedOrder.setOrderDetails(details);
        
        // Dòng này cực kỳ quan trọng: Ghi đè vào DB và trả ra object mới nhất có chứa giá đã giảm
        savedOrder = orderRepository.save(savedOrder); 
        
        OrderResponse response = mapToResponse(savedOrder);

        if ("VNPAY".equalsIgnoreCase(request.getPaymentMethod())) {
            // Lúc này savedOrder đã ngậm giá đã trừ, ném vào sinh link VNPay là chuẩn 100%
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

    // =========================================================================
    // 🎯 HÀM QUYỀN LỰC: CẬP NHẬT TRẠNG THÁI TỪ VNPAY 
    // (ĐÃ BỌC LÓT VÉ, HOÀN KHO VÀ XỬ LÝ MÃ GIẢM GIÁ TẬN GỐC)
    // =========================================================================
  @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng không tồn tại"));
        
        String status = newStatus.toUpperCase();
        boolean isCancelStatus = "CANCELLED".equals(status) || "FAILED".equals(status);
        boolean isSuccessStatus = "PAID".equals(status);

        // Chặn luồng nếu đơn đã bị Hủy/Fail từ trước, tránh cộng kho 2 lần
        if (("CANCELLED".equals(order.getStatus()) || "FAILED".equals(order.getStatus())) && isCancelStatus) {
            return mapToResponse(order);
        }

        order.setStatus(status);

        if (order.getOrderDetails() != null) {
            List<Ticket> ticketsToUpdate = new ArrayList<>();
            
            for (OrderDetail d : order.getOrderDetails()) {
                
                // 1. 🎯 CẬP NHẬT ĐỒNG BỘ TRẠNG THÁI VÉ (Tìm đích danh vé đang BOOKED của User này)
                if ("TICKET".equals(d.getItemType())) {
                    // Xóa hẳn cái showtimeId đi, dùng ID ghế và ID user để tóm chính xác vé
                    List<Ticket> tickets = ticketRepository.findBySeatIdAndUserIdAndStatus(
                            d.getItemId(), 
                            order.getUser().getUserId(), 
                            "BOOKED"
                    );
                    
                    for (Ticket t : tickets) {
                        t.setStatus(status); // Đổi thành PAID, CANCELLED hoặc FAILED
                        ticketsToUpdate.add(t);
                    }
                }
                
                // 2. NẾU BỊ HỦY/THẤT BẠI -> HOÀN LẠI KHO BẮP NƯỚC THEO ĐÚNG RẠP
                if (isCancelStatus && "COMBO".equals(d.getItemType())) {
                    Long cinemaItemId = order.getCinemaItem().getId(); 
                    cinemaComboRepository.findByCinemaItemIdAndComboId(cinemaItemId, d.getItemId())
                        .ifPresent(cinemaCombo -> {
                            if (cinemaCombo.getStock() != null) {
                                cinemaCombo.setStock(cinemaCombo.getStock() + d.getQuantity());
                                cinemaComboRepository.save(cinemaCombo);
                            }
                        });
                }
                
                // 3. NẾU THANH TOÁN THÀNH CÔNG -> CHỐT SỔ VOUCHER CỦA USER
                if (isSuccessStatus && "VOUCHER".equals(d.getItemType())) {
                    Voucher voucher = voucherRepository.findById(d.getItemId()).orElse(null);
                    if (voucher != null) {
                        // Tăng lượt đã dùng lên (tương đương trừ đi số lượng kho Voucher)
                        voucher.setUsedCount(voucher.getUsedCount() + 1);
                        voucherRepository.save(voucher);
                        
                        // Xóa vĩnh viễn voucher này khỏi ví của User (bảng user_vouchers)
                        User orderUser = order.getUser();
                        orderUser.getVouchers().removeIf(v -> v.getId().equals(voucher.getId()));
                        userRepository.save(orderUser);
                    }
                }
            }
            
            // Lưu toàn bộ vé đã đổi trạng thái
            if (!ticketsToUpdate.isEmpty()) {
                ticketRepository.saveAll(ticketsToUpdate);
            }
        }
        
        Order savedOrder = orderRepository.save(order);
        
        // 4. Cộng điểm thưởng khi thanh toán thành công
        if ("PAID".equals(status)) {
            User user = savedOrder.getUser();
            int earnedPoints = (int) (savedOrder.getTotalAmount() / 10000);
            user.setPoints(user.getPoints() + earnedPoints);
            userRepository.save(user);
        }
        
        // 5. Cập nhật bảng Payment
        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(savedOrder.getId());
        Payment payment = paymentOpt.orElse(new Payment());
        payment.setOrder(savedOrder);
        payment.setAmount(savedOrder.getTotalAmount());
        payment.setStatus(status);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);
        
        // 6. Thông báo ngầm (Mail/Socket)
        if ("PAID".equals(status) || "USED".equals(status)) {
            if ("PAID".equals(status)) mailService.sendOrderConfirmation(savedOrder);

            try {
                Map<String, Object> adminNotify = new HashMap<>();
                adminNotify.put("message", "Cập nhật trạng thái đơn hàng!");
                adminNotify.put("orderId", savedOrder.getId());
                adminNotify.put("customer", savedOrder.getUser().getFirstName() + " " + savedOrder.getUser().getLastName());
                adminNotify.put("total", savedOrder.getTotalAmount());
                adminNotify.put("status", status);
                messagingTemplate.convertAndSend("/topic/admin-notifications", adminNotify, new HashMap<>());
            } catch (Exception e) {
                System.err.println("Thông báo ngầm lỗi: " + e.getMessage());
            }
        }

        return mapToResponse(savedOrder);
    }
    // =========================================================================
    // HỦY ĐƠN THỦ CÔNG (Được gom chung vào updateOrderStatus để tái sử dụng logic)
    // =========================================================================
    @Transactional
    public String cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new RuntimeException("Đơn hàng này đã được hủy trước đó!");
        }

        // Gọi thẳng hàm quyền lực ở trên để xử lý mượt mà cả Hủy vé lẫn Hoàn kho bắp nước
        updateOrderStatus(orderId, "CANCELLED");
        
        return "Hủy đơn hàng thành công, đã hoàn trả ghế và bắp nước vào kho!";
    }

    // =========================================================================
    // CÁC HÀM GET, SCAN, CHECK-IN GIỮ NGUYÊN (KHÔNG ẢNH HƯỞNG)
    // =========================================================================
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
        
        if (isSuperAdmin(staff)) {
            throw new RuntimeException("Tài khoản Super Admin không có quyền soát vé trực tiếp tại quầy!");
        }

        if (sampleTicket.getShowtime() == null || sampleTicket.getShowtime().getCinemaItem() == null) {
            throw new RuntimeException("Dữ liệu suất chiếu hoặc cơ sở rạp đính kèm vé bị lỗi!");
        }

        Long ticketCinemaId = sampleTicket.getShowtime().getCinemaItem().getId();
        if (staff.getManagedCinemaItemId() == null || !staff.getManagedCinemaItemId().equals(ticketCinemaId)) {
            throw new RuntimeException("Xâm nhập sai chi nhánh! Vé này được mua tại cụm rạp '" + sampleTicket.getShowtime().getCinemaItem().getName() + "', không thể soát tại đây.");
        }

        Seat seat = sampleTicket.getSeat();
        if (seat == null) {
            throw new RuntimeException("Dữ liệu vật lý ghế ngồi đính kèm mã đặt vé bị lỗi!");
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

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng không tồn tại"));

        List<Long> showtimeIds =
                orderRepository.findShowtimeIdByOrderId(order.getId());

        Long showtimeId =
                showtimeIds.isEmpty() ? null : showtimeIds.get(0);

        String foundBookingCode = "N/A";

        if (order.getOrderDetails() != null && showtimeId != null) {

            for (OrderDetail d : order.getOrderDetails()) {

                if ("TICKET".equals(d.getItemType())) {

                    List<Ticket> tickets =
                            ticketRepository.findBySeatIdAndShowtimeId(
                                    d.getItemId(),
                                    showtimeId
                            );

                    Optional<Ticket> correctTicket = tickets.stream()
                            .filter(t -> t.getUser() != null
                                    && t.getUser().getUserId()
                                    .equals(order.getUser().getUserId()))
                            .max(Comparator.comparing(Ticket::getId));

                    if (correctTicket.isPresent()) {
                        foundBookingCode =
                                correctTicket.get().getBookingCode();
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
        List<Long> showtimeIds = orderRepository.findShowtimeIdByOrderId(order.getId());
        Long showtimeId = showtimeIds.isEmpty() ? null : showtimeIds.get(0);
        String realBookingCode = null; 

        if (order.getOrderDetails() != null) {
            for (OrderDetail od : order.getOrderDetails()) {
                if ("TICKET".equals(od.getItemType()) && od.getItemId() != null) {
                    Optional<Ticket> myTicket = ticketRepository.findBySeatIdAndShowtimeId(od.getItemId(), showtimeId)
                            .stream()
                            .filter(t -> t.getUser() != null && t.getUser().getUserId().equals(order.getUser().getUserId()))
                            .max(Comparator.comparing(Ticket::getId)); 

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
            } else if ("COMBO".equals(d.getItemType())) {
                name = comboRepository.findById(d.getItemId())
                        .map(Combo::getName)
                        .orElse("Combo Bắp Nước");
            } else if ("VOUCHER".equals(d.getItemType())) {
                name = "Voucher Giảm Giá (" + d.getItemName() + ")";
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