package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.authencation.LoginRequest;

import com.group6.Rental_Car.dtos.authencation.LoginResponse;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.services.Jwt.MailService;
import com.group6.Rental_Car.services.authencation.UserService;
import com.group6.Rental_Car.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final MailService mailService;

    // Step 1: Login -> gửi token qua email, không trả về FE
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userService.findByEmail(request.getEmail())
                .filter(user -> request.getPassword().equals(user.getPassword()));

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Sai email hoặc mật khẩu!");
        }

        User user = userOpt.get();
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        // Gửi token qua email
        mailService.sendToken(user.getEmail(), token);

        // Trả về link verify-token
        return ResponseEntity.ok(new Object() {
            public final String message = "Đăng nhập thành công. Token đã gửi vào email!";
            public final String verifyUrl = "/auth/verify-token";
        });
    }

    // Step 2: Verify token -> xác định role -> redirect
    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyToken(@RequestParam String token) {
        try {
            Claims claims = jwtUtil.validateToken(token);
            String role = claims.get("role", String.class);

            String redirectUrl;
            switch (role.toUpperCase()) {
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

            return ResponseEntity.ok(new Object() {
                public final String redirect = redirectUrl;
                public final String roleName = role;
            });

        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token không hợp lệ hoặc đã hết hạn!");
        }
    }
}
