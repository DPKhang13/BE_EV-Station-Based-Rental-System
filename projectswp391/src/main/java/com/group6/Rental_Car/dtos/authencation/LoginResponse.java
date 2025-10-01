package com.group6.Rental_Car.dtos.authencation;

import com.group6.Rental_Car.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class LoginResponse {
    private Integer userId;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private String kycStatus;
    private String token; // JWT token
}