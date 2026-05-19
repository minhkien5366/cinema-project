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
            seatService.validateSeatSelection(request.getShowtimeId(), request.getSeatIds());
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

                // 🔥 UPDATE ĐẮT GIÁ: Ghi đè đầy đủ 3 trường dữ liệu Snapshot của ghế vào cuống vé Ticket
                Ticket ticket = Ticket.builder()
                    .seat(seat)
                    .showtime(showtime)
                    .user(user)
                    .price(dynamicPrice)
                    .status("BOOKED")
                    .bookingCode(commonBookingCode) 
                    .seatRow(seat.getSeatRow())       // Lưu Snapshot Hàng ghế
                    .seatNumber(seat.getSeatNumber()) // Lưu Snapshot Số ghế
                    .seatName(seat.getName())         // Lưu Snapshot Tên ghế (Ví dụ: A5)
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

        if (order.getOrderDetails() != null) {
            for (OrderDetail d : order.getOrderDetails()) {
                if ("TICKET".equals(d.getItemType())) {
                    ticketRepository.findBySeatIdAndShowtimeId(d.getItemId(), orderRepository.findShowtimeIdByOrderId(order.getId()))
                        .ifPresent(t -> {
                            if ("PAID".equals(status)) {
                                t.setStatus("PAID");
                            } else if ("CANCELLED".equals(status)) {
                                t.setStatus("CANCELLED");
                            } else if ("USED".equals(status)) {
                                t.setStatus("USED");
                            }
                            ticketRepository.save(t);
                        });
                }
            }
        }
        
        Order savedOrder = orderRepository.save(order);
        
        if ("PAID".equals(status)) {
            mailService.sendOrderConfirmation(savedOrder);

            Map<String, Object> adminNotify = new HashMap<>();
            adminNotify.put("message", "Có đơn hàng mới!");
            adminNotify.put("orderId", savedOrder.getId());
            adminNotify.put("customer", savedOrder.getUser().getFirstName() + " " + savedOrder.getUser().getLastName());
            adminNotify.put("total", savedOrder.getTotalAmount());
            
            messagingTemplate.convertAndSend((String) "/topic/admin-notifications", (String) "/topic/admin-notifications", adminNotify);        
        }

        return mapToResponse(savedOrder);
    }

    // ================= 🔥 LUỒNG NÂNG CAO 1: KIỂM TRA QUÉT MÃ QR TẠI QUẦY =================
    @Override
    public OrderResponse scanOrderTicket(Long orderId) {
        User staff = getCurrentUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng #" + orderId + " không tồn tại trên hệ thống!"));

        // RÀNG BUỘC 1: Super Admin tuyệt đối không được tự ý thực hiện soát vé tại quầy
        if (isSuperAdmin(staff)) {
            throw new RuntimeException("Tài khoản Super Admin không có quyền soát vé trực tiếp tại quầy!");
        }

        // RÀNG BUỘC 2: Chỉ cho phép quét đúng chi nhánh rạp được giao quản lý
        if (staff.getManagedCinemaItemId() == null || !staff.getManagedCinemaItemId().equals(order.getCinemaItem().getId())) {
            throw new RuntimeException("Xâm nhập sai chi nhánh! Vé này thuộc cụm rạp '" + order.getCinemaItem().getName() + "'.");
        }

        // RÀNG BUỘC 3: Bóc tách và từ chối chi tiết theo từng trạng thái lỗi
        String currentStatus = order.getStatus().toUpperCase();
        switch (currentStatus) {
            case "USED":
                throw new RuntimeException("CẢNH BÁO GIAN LẬN: Mã QR này đã được quét sử dụng trước đó!");
            case "CANCELLED":
                throw new RuntimeException("Vé lỗi: Đơn hàng này đã bị hủy bỏ hoặc hoàn tiền trên hệ thống!");
            case "PENDING":
                throw new RuntimeException("Vé chưa kích hoạt: Đơn hàng vẫn ở trạng thái chờ thanh toán!");
            case "PAID":
                break; 
            default:
                throw new RuntimeException("Trạng thái đơn hàng không hợp lệ để soát vé: " + currentStatus);
        }

        // RÀNG BUỘC NÂNG CAO 4: Kiểm tra thời hạn suất chiếu (Hết hạn quá 2 tiếng không cho soát vé)
        Long showtimeId = orderRepository.findShowtimeIdByOrderId(order.getId());
        if (showtimeId != null) {
            showtimeRepository.findById(showtimeId).ifPresent(showtime -> {
                if (showtime.getStartTime().plusHours(2).isBefore(LocalDateTime.now())) {
                    throw new RuntimeException("Vé không còn giá trị! Suất chiếu của phim này đã kết thúc.");
                }
            });
        }

        return mapToResponse(order);
    }

    // ================= 🔥 LUỒNG NÂNG CAO 2: XÁC NHẬN SỬ DỤNG VÉ (BẤM NÚT TRÊN FE) =================
    @Override
    @Transactional
    public OrderResponse confirmCheckIn(Long orderId) {
        // Thực hiện lại một lượt kiểm tra an toàn (Idempotency) để phòng ngừa bấm nút 2 lần
        scanOrderTicket(orderId);
        
        // Gọi hàm update có sẵn để đồng bộ Order + Tickets sang trạng thái USED
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

    // ================= 🔥 UPDATE ĐẮT GIÁ: ĐỔI TÊN ITEM THÀNH SNAPSHOT GHẾ THỰC TẾ =================
    private OrderResponse mapToResponse(Order order) {
        // Lấy ID suất chiếu để phục vụ truy vấn Ticket Snapshot
        Long showtimeId = orderRepository.findShowtimeIdByOrderId(order.getId());

        List<OrderDetailResponse> detailResponses = order.getOrderDetails().stream().map(d -> {
            String name = "";
            
            if ("TICKET".equals(d.getItemType())) {
                // 🎯 ĐỌC SNAPSHOT CỨNG: Lôi thẳng thông tin hàng ghế, số ghế từ Snapshot lưu trong Ticket ra
                if (showtimeId != null) {
                    name = ticketRepository.findBySeatIdAndShowtimeId(d.getItemId(), showtimeId)
                            .map(t -> "Ghế " + t.getSeatName() + " (Hàng " + t.getSeatRow() + " - Số " + t.getSeatNumber() + ")")
                    .orElse("Vé Xem Phim");
                } else {
                    name = "Vé Xem Phim";
                }
            } else {
                // Đọc tên Combo từ bảng Combo
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
                    .itemName(name) // Nạp chuỗi text mô tả chi tiết vật phẩm sang DTO trả về cho FE
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
                .orderDetails(detailResponses) // Trả về danh sách chi tiết vật phẩm đã bồi tên xịn
                .build();
    }
}