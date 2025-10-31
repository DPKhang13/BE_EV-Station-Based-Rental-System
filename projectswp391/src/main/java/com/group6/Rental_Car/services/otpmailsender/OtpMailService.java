package com.group6.Rental_Car.services.otpmailsender;

public interface OtpMailService {
    String generateAndSendOtp(String email);    // sinh + gửi OTP
    boolean validateOtp(String email, String otp); // kiểm tra OTP
    void clearOtp(String otp);                 // xoá OTP sau khi dùng
    String getEmailByOtp(String otp);// tra ngược email từ OTP

}