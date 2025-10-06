package com.group6.Rental_Car.dtos.vehicle;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VehicleCreateRequest {

    private String plateNumber; // dùng khi create / update
    private String status;      // tùy chọn
    private Integer stationId;
    private Integer seatCount;
    private String variant;// dùng khi create / update
}
