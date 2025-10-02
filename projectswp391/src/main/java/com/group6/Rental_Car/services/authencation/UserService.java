package com.group6.Rental_Car.services.authencation;

import com.group6.Rental_Car.dtos.LoginPage.LoginRequest;
import com.group6.Rental_Car.dtos.LoginPage.RegisterRequest;
import com.group6.Rental_Car.dtos.LoginPage.RegisterResponse;
import com.group6.Rental_Car.dtos.OtpVerify.OtpRequest;

import java.util.UUID;

public interface UserService {
    RegisterResponse register(RegisterRequest request);
    RegisterResponse login(LoginRequest request);
    RegisterResponse verifyOtp(OtpRequest request);
    void logout(UUID userId);
    RegisterResponse getUserDetails(UUID userId);
}
