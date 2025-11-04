package com.group6.Rental_Car.dtos.verifyfile;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVerificationResponse {
    private UUID userId;
    private String fullName;
    private String phone;
    private String email;
    private String status;
    private String role;
    private String idCardUrl;
    private String driverLicenseUrl;
    private String userStatus;
}