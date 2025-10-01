package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.authencation.RegisterRequest;
import com.group6.Rental_Car.dtos.authencation.RegisterResponse;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.enums.UserStatus;
import com.group6.Rental_Car.services.authencation.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/register")
@RequiredArgsConstructor
public class RegisterController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // Check email đã tồn tại chưa
        if (userService.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(" Email đã tồn tại!");
        }

        // Map RegisterRequest -> User
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhoneNumber());
        user.setPassword(request.getPassword());
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.NEED_OTP); // Chưa active, cần OTP sau khi login

        //  Lưu user vào DB
        userService.register(user);

        return ResponseEntity.ok(new RegisterResponse(
                "Đăng ký thành công! Vui lòng đăng nhập để nhận OTP và kích hoạt tài khoản.",
                "/login",
                0
        ));
    }
}
