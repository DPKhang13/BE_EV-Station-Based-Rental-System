package com.group6.Rental_Car.dtos.orderservice;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderServiceRequest {
    private UUID orderId;
    private Long detailId;
    private Long vehicleId;
    private String serviceType;   // MAINTENANCE | CLEANING | REPAIR | INCIDENT | OTHER
    private String description;
    private BigDecimal cost;
    private UUID performedById;
    private Integer stationId;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private String status;       // pending | processing | done | cancelled
    private String note;
}