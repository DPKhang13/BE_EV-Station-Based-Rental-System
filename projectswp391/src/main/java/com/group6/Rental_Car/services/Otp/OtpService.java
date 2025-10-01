package com.group6.Rental_Car.services.Otp;

public interface OtpService {
    String generateOtp(String email);
    String getEmailByOtp(String otp);
    boolean validateOtp(String otp);
    void clearOtp(String email);
}
