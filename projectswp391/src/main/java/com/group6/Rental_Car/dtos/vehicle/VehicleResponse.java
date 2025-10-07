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
    private String description;

    // ===== Thuộc tính từ bảng vehicleattribute =====
    private String brand;
    private String color;
    private String transmission;
    private Integer seatCount;
    private Integer year;
    private String variant;
    private String batteryStatus;
    private String batteryCapacity;
    private Integer rangeKm;
}
