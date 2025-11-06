package com.group6.Rental_Car.dtos.stafflist;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {
    private String staffId;
    private String staffName;
    private String staffEmail;
    private String role;
    private String stationName;
    private Long pickupCount;
    private Long returnCount;
    private String status;
}
