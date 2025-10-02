package com.group6.Rental_Car.dtos.LoginPage;

import lombok.*;

@Data
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String fullName;
    private String email;
    private String phone;

}
