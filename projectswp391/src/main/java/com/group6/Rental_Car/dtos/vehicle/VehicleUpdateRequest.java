package com.group6.Rental_Car.dtos.vehicle;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VehicleUpdateRequest {
    private String status;
    private String variant;
    private Integer seatCount;
    private Integer stationId;
}
