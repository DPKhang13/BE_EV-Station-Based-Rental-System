package com.group6.Rental_Car.enums;

public enum UserStatus {
    PENDING,   // Đăng ký nhưng chưa xác thực OTP
    ACTIVE,    // Đã xác thực OTP (login không cần OTP nữa)
    NEED_OTP,
    VERIFIED// User cũ, login phải nhập OTP
}
