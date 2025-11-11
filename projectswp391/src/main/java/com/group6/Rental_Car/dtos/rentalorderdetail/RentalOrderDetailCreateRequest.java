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
public class RentalOrderDetailCreateRequest {

    private UUID orderId;
    private Long vehicleId;
    private String type;            // DEPOSIT | RENTAL | RETURN | SERVICE | OTHER
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal price;       // có thể null -> mặc định 0
    private String description;
    private String status;
}
