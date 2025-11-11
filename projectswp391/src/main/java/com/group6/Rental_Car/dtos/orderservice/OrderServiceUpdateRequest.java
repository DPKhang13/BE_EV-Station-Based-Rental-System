package com.group6.Rental_Car.dtos.orderservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderServiceUpdateRequest {
    private Long vehicleId;
    private Integer stationId;
    private String serviceType;
    private String description;
    private BigDecimal cost;
    private UUID performedBy;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private String status;
    private String note;

    private UUID orderId;
    private Long detailId;
}
