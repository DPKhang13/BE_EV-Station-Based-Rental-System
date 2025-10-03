package com.group6.Rental_Car.dtos.VerifyToken;

import com.group6.Rental_Car.enums.OtpType;
import lombok.Data;

@Data
public class OtpRequest {
    private String otp;
    private OtpType type; // REGISTER, LOGIN
}
