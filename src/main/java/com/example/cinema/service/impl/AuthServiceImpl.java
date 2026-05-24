package com.example.cinema.service.impl;

import com.example.cinema.dto.*;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.security.JwtTokenProvider;
import com.example.cinema.service.AuthService;
import com.example.cinema.service.AuthMailService; // 🎯 IMPORT AuthMailService
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    
    // 🎯 INJECT AuthMailService ĐỂ GỬI MAIL
    private final AuthMailService authMailService;

    // 🎯 TẠO BỘ NHỚ TẠM (CACHE) ĐỂ LƯU OTP VÀ THỜI GIAN HẾT HẠN
    private final Map<String, OtpDetails> otpCache = new ConcurrentHashMap<>();

    // Class nội bộ dùng để lưu trữ thông tin OTP
    private static class OtpDetails {
        String otpCode;
        LocalDateTime expiryTime;

        OtpDetails(String otpCode, LocalDateTime expiryTime) {
            this.otpCode = otpCode;
            this.expiryTime = expiryTime;
        }
    }

    @Override
    public JwtResponse login(LoginRequest loginRequest) { 
        try {
            Authentication authentication =
                authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail().trim(),
                        loginRequest.getPassword())
                    );
            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
            String token =
                    jwtTokenProvider.generateToken(authentication);

            List<String> roles =
                    authentication.getAuthorities()
                            .stream()
                            .map(item -> item.getAuthority())
                            .collect(Collectors.toList());

            return JwtResponse.builder()
                    .token(token)
                    .email(loginRequest.getEmail())
                    .roles(roles)
                    .type("Bearer")
                    .build();
        } catch (BadCredentialsException e) {
            throw new RuntimeException(
                    "Email hoặc mật khẩu không chính xác!"
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "Đăng nhập thất bại!"
            );
        }
    }

    @Override
    @Transactional
    public String register(RegisterRequest registerRequest) {
        // 1. Kiểm tra email tồn tại
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email đã tồn tại!");
        }

        // 2. Tạo đối tượng User mới
        User user = new User();
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setMobileNumber(registerRequest.getMobileNumber());
        user.setGender(registerRequest.getGender());
        user.setDateOfBirth(registerRequest.getDateOfBirth());
        user.setAvatar(registerRequest.getAvatar());

        // 3. Xử lý gán quyền: LUÔN MẶC ĐỊNH LÀ ROLE_USER
        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy ROLE_USER trong hệ thống."));
        roles.add(userRole);

        user.setRoles(roles);
        userRepository.save(user);
        return "Đăng ký tài khoản thành công!";
    }

    // ==========================================
    // 🎯 YÊU CẦU GỬI OTP QUÊN MẬT KHẨU
    // ==========================================
    @Override
    public String forgotPassword(String email) {
        // 1. Kiểm tra tài khoản có tồn tại không
        if (!userRepository.existsByEmail(email)) {
            throw new RuntimeException("Tài khoản email này không tồn tại trong hệ thống!");
        }

        // 2. Sinh mã OTP ngẫu nhiên 6 chữ số
        String otpCode = String.format("%06d", new Random().nextInt(999999));
        
        // 3. Lưu vào bộ nhớ tạm thời, thời gian sống là 5 phút
        otpCache.put(email, new OtpDetails(otpCode, LocalDateTime.now().plusMinutes(5)));

        // 4. Gọi Service gửi Mail đi
        authMailService.sendOtpEmail(email, otpCode);

        return "Mã xác thực OTP đã được gửi đến email của bạn. Mã có hiệu lực trong 5 phút.";
    }

    // ==========================================
    // 🎯 ĐẶT LẠI MẬT KHẨU MỚI
    // ==========================================
    @Override
    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail();
        OtpDetails otpDetails = otpCache.get(email);

        // 1. Kiểm tra xem người dùng có yêu cầu OTP trước đó không
        if (otpDetails == null) {
            throw new RuntimeException("Mã xác thực không hợp lệ hoặc chưa được yêu cầu!");
        }

        // 2. Kiểm tra thời gian hết hạn của OTP
        if (LocalDateTime.now().isAfter(otpDetails.expiryTime)) {
            otpCache.remove(email); // Xóa OTP hết hạn
            throw new RuntimeException("Mã xác thực đã hết hạn! Vui lòng yêu cầu gửi lại mã mới.");
        }

        // 3. So sánh mã OTP người dùng nhập vào
        if (!otpDetails.otpCode.equals(request.getOtpCode())) {
            throw new RuntimeException("Mã xác thực OTP không chính xác!");
        }

        // 4. Xác thực thành công -> Cập nhật mật khẩu mới
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng."));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 5. Xóa OTP khỏi bộ nhớ sau khi dùng thành công để tránh tái sử dụng
        otpCache.remove(email);

        return "Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại bằng mật khẩu mới.";
    }
}