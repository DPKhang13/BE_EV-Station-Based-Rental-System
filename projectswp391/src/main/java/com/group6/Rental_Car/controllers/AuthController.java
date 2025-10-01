package com.group6.Rental_Car.controllers;
import com.group6.Rental_Car.dtos.VerifyToken.OtpRequest;
import com.group6.Rental_Car.dtos.VerifyToken.VerifyResponse;
import com.group6.Rental_Car.dtos.authencation.LoginRequest;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.OtpType;
import com.group6.Rental_Car.enums.UserStatus;
import com.group6.Rental_Car.services.Jwt.MailService;
import com.group6.Rental_Car.services.Otp.OtpService;
import com.group6.Rental_Car.services.authencation.UserService;
import com.group6.Rental_Car.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final MailService mailService;
    private final OtpService otpService;

    // Step 1: Login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userService.findByEmail(request.getEmail())
                .filter(user -> request.getPassword().equals(user.getPassword()));

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Sai email hoặc mật khẩu!");
        }

        User user = userOpt.get();

        if (user.getStatus() == UserStatus.ACTIVE) {
            // User active -> login trực tiếp
            String jwtToken = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
            return ResponseEntity.ok(buildResponse(user, jwtToken));
        }

        // Nếu NEED_OTP thì gửi OTP để verify
        String otp = otpService.generateOtp(user.getEmail(), OtpType.LOGIN, null);
        mailService.sendOtp(user.getEmail(), otp);

        return ResponseEntity.ok(Map.of(
                "message", "🔐 OTP đã được gửi tới email, vui lòng xác nhận để tiếp tục.",
                "verifyUrl", "/verify-otp"
        ));
    }

    // Step 2: Verify OTP
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpRequest request) {
        String otp = request.getOtp();
        if (otp == null || otp.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(" OTP is required");
        }

        if (!otpService.validateOtp(otp)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(" OTP không hợp lệ hoặc đã hết hạn!");
        }

        String email = otpService.getEmailByOtp(otp);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(" Không tìm thấy email cho OTP này!");
        }

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(" Không tìm thấy user!"));

        // Nếu user chưa active -> active luôn
        if (user.getStatus() == UserStatus.NEED_OTP) {
            user.setStatus(UserStatus.ACTIVE);
            userService.save(user);
        }

        otpService.clearOtp(otp);

        String jwtToken = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(buildResponse(user, jwtToken));
    }

    // Helper để trả response gọn
    private VerifyResponse buildResponse(User user, String jwtToken) {
        String redirectUrl = switch (user.getRole().name().toUpperCase()) {
            case "CUSTOMER" -> "/customer";
            case "STAFF" -> "/staff";
            case "ADMIN" -> "/admin";
            default -> "/login";
        };

        return new VerifyResponse(
                jwtToken,
                user.getRole().name(),
                redirectUrl,
                user.getFullName(),
                user.getPhone(),
                user.getEmail()
        );
    }
}
