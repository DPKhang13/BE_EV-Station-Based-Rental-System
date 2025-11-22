package com.group6.Rental_Car.dtos.vehicle;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VehicleResponse {
    private Long vehicleId;
    private Integer stationId;
    private String stationName;
    private String plateNumber;
    private String status;
    private String vehicleName;
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
    private String carmodel;
    private Integer pricingRuleId;
    private String imageUrl; // URL ảnh xe (có thể là JSON array nếu nhiều ảnh)
}
