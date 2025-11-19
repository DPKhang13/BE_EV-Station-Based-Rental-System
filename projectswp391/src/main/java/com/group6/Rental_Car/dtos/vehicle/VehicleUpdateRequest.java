package com.group6.Rental_Car.dtos.vehicle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VehicleUpdateRequest {

    // ===== Thông tin bảng vehicle =====
    private String status;
    private Integer stationId;

    // ===== Thông tin thuộc tính (vehicleattribute) =====
    private String brand;
    private String color;
    private Integer seatCount;
    private String variant;
    private String batteryStatus;
    private String batteryCapacity;
    private String carmodel;
}
