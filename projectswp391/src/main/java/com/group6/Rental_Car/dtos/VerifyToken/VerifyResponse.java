package com.group6.Rental_Car.dtos.VerifyToken;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyResponse {
    private String token;
    private String role;
    private String redirect;
    private String fullname;
    private String phone;
    private String email;
}
