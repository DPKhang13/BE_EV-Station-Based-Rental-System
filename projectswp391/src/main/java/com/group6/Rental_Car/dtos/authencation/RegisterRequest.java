package com.group6.Rental_Car.dtos.authencation;
import lombok.Data;
@Data
public class RegisterRequest {
    private String fullName;
    private String email;
    private String phoneNumber;
    private String password;
}
