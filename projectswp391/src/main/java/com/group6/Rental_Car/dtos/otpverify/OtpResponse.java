package com.group6.Rental_Car.dtos.otpverify;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OtpResponse {
    private String redirect;
    private String fullname;
    private String phone;
    private String email;
}
