package com.group6.Rental_Car.dtos.vehicle;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleRequest {
    private String plateNumber;
    private String status;
    private int seatCount;
    private String variant;
}
