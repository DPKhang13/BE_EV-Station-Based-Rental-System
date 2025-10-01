package com.group6.Rental_Car.services.Otp;

import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.OtpType;

public interface OtpService {
    String generateOtp(String email, OtpType type, User pendingUser);
    String getEmailByOtp(String otp);
    boolean validateOtp(String otp);
    void clearOtp(String otp);

    // Dành cho đăng ký
    User getPendingUser(String email);
    void removePendingUser(String email);
}
