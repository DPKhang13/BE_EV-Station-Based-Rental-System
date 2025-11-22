package com.group6.Rental_Car.dtos.vehicle;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VehicleDetailResponse {
    // ===== Thông tin xe =====
    private Long vehicleId;
    private Integer stationId;
    private String stationName;
    private String plateNumber;
    private String status;
    private String vehicleName;
    private String description;
    private String imageUrl; // URL ảnh xe (có thể là JSON array nếu nhiều ảnh)

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

    // ===== Thông tin đơn thuê (nếu có) =====
    private boolean hasBooking;  // true nếu có đơn thuê đang diễn ra
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rentalStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rentalEndDate;
    private String rentalOrderStatus;
    private String bookingNote; // "Chưa có đơn thuê" hoặc thông tin đơn thuê
}

