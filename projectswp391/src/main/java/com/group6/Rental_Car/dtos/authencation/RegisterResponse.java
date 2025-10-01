package com.group6.Rental_Car.dtos.authencation;

import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class RegisterResponse {
    private String message;
    private String redirectUrl;
    private int delaySeconds;
}
