package com.group6.Rental_Car.dtos.loginpage;

import lombok.*;

import java.util.UUID;

@Data
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String fullName;
    private String email;
    private String phone;
    private UUID userId;
    private String role;
}
