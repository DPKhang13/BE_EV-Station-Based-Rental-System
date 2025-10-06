package com.group6.Rental_Car.dtos.vehicle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VehicleResponse {
    private Long vehicleId;
    private Integer stationId;
    private String plateNumber;
    private String status;
    private Integer seatCount;
    private String variant;
}
