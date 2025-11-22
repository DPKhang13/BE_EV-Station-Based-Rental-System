package com.group6.Rental_Car.dtos.stafflist;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {

    private Object staffId;
    private String staffName;
    private String staffEmail;
    private String staffPhone;
    private String role;
    private String stationName;
    private Long pickupCount;
    private Long returnCount;
    private String status;
}