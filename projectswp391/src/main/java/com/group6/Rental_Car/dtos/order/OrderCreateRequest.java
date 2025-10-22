package com.group6.Rental_Car.dtos.order;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;
@Data
@AllArgsConstructor
public class OrderCreateRequest {
    private UUID customerId;        // ID khách hàng đặt xe
    private Long vehicleId;         // ID xe được chọn
    private LocalDateTime startTime; // Thời gian bắt đầu thuê
    private LocalDateTime endTime;   // Thời gian kết thúc thuê
    private String couponCode;        // Mã giảm giá (có thể null nếu không dùng)
    private Integer plannedHours;
    private Integer actualHours;
}
