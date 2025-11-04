package com.group6.Rental_Car.dtos.vehicle;

import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class VehicleUpdateStatusRequest {
    private String status;
    private String batteryStatus;
}
