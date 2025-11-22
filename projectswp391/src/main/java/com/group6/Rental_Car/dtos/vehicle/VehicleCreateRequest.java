package com.group6.Rental_Car.dtos.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleCreateRequest {

    private String plateNumber;
    private String status;
    private Integer stationId;
    private String vehicleName;
    private String description;

    private String brand;
    private String color;
    private String transmission = "automatic";
    private Integer seatCount;
    private Integer year = 2025;
    private String variant;
    private String batteryStatus;
    private String batteryCapacity;
    private String carmodel;
    
    // Ảnh xe (có thể là null nếu không upload)
    // Frontend sẽ gửi qua @RequestPart trong controller
}

