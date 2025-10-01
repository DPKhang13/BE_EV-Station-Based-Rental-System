package com.group6.Rental_Car.controllers;
import com.group6.Rental_Car.dtos.VerifyToken.VerifyResponse;
import com.group6.Rental_Car.dtos.authencation.LoginRequest;
import com.group6.Rental_Car.entities.User;
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

    // Step 1: Login -> gửi token qua email, không trả về FE
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userService.findByEmail(request.getEmail())
                .filter(user -> request.getPassword().equals(user.getPassword()));

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Sai email hoặc mật khẩu!");
        }

        User user = userOpt.get();

        String otp = otpService.generateOtp(user.getEmail());
        // Gửi token qua email
        mailService.sendOtp(user.getEmail(), otp);

        // Trả về link verify-token
        return ResponseEntity.ok(new Object() {
            public final String message = "Đăng nhập thành công. OTP đã gửi vào email!";
            public final String verifyUrl = "/auth/verify-otp";
        });
    }

    // Step 2: Verify token -> xác định role -> redirect
    @PostMapping ("/verify-otp")

    public ResponseEntity<?> verifyToken(@RequestBody Map<String, String> request) {
        String otp = request.get("otp");
        if (otp == null || otp.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("OTP is required");
        }
        else if (!otpService.validateOtp(otp)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("OTP không hợp lệ hoặc đã hết hạn!");
        }

        // Lấy email từ OTP
        String email = otpService.getEmailByOtp(otp);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy email cho OTP này!");
        }

        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Email không tồn tại!");
        }

        User user = userOpt.get();

        // Xoá OTP sau khi dùng
        otpService.clearOtp(otp);

        // Sinh JWT
        final String jwtToken = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        final String roleName = user.getRole().name();

        // Điều hướng theo role
        final String redirectUrl;
        switch (roleName.toUpperCase()) {
            case "CUSTOMER":
                redirectUrl = "/customer";
                break;
            case "STAFF":
                redirectUrl = "/staff";
                break;
            case "ADMIN":
                redirectUrl = "/admin";
                break;
            default:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Role không hợp lệ!");
        }

        VerifyResponse response = new VerifyResponse(
                jwtToken,
                roleName,
                redirectUrl,
                user.getFullName(),
                user.getPhone(),
                user.getEmail()
        );

        return ResponseEntity.ok(response);
    }
}
