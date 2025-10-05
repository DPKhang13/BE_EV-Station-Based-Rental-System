package com.group6.Rental_Car.services.authencation;

import com.group6.Rental_Car.dtos.loginpage.LoginRequest;
import com.group6.Rental_Car.dtos.loginpage.RegisterRequest;
import com.group6.Rental_Car.dtos.loginpage.RegisterResponse;
import com.group6.Rental_Car.dtos.otpverify.OtpRequest;
import com.group6.Rental_Car.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserService  {
    RegisterResponse register(RegisterRequest request);
    RegisterResponse login(LoginRequest request);
    RegisterResponse verifyOtp(OtpRequest request);
    void logout(UUID userId);
    RegisterResponse getUserDetails(UUID userId);
}
