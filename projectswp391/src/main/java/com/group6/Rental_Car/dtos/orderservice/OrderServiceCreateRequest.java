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
public class OrderServiceCreateRequest {
    private Long vehicleId;
    private Integer stationId;
    private String serviceType;
    private String description;
    private BigDecimal cost;
    private UUID performedBy;           // user_id
    private LocalDateTime occurredAt;
    private String status;              // pending/processing/done/cancelled
    private String note;

    private UUID orderId;
    private Long detailId;
}
