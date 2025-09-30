package com.group6.Rental_Car.services.Jwt;

public interface MailService {
    void sendToken(String toEmail, String token);
}

