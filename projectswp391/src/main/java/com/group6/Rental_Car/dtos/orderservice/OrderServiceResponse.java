package com.group6.Rental_Car.dtos.orderservice;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderServiceResponse {
    private Long serviceId;
    private UUID orderId;
    private Long detailId;
    private Long vehicleId;
    private String serviceType;
    private String description;
    private BigDecimal cost;
    private String performedByName;
    private String stationName;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private String status;
    private String note;
}
