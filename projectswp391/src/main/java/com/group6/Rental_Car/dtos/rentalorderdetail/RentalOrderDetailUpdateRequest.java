package com.group6.Rental_Car.dtos.rentalorderdetail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RentalOrderDetailUpdateRequest {
    private UUID orderId;           // optional đổi đơn
    private Long vehicleId;         // optional đổi xe
    private String type;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal price;
    private String description;
    private String status;
}
