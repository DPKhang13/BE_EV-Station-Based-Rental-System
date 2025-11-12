package com.group6.Rental_Car.dtos.orderservice;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderServiceCreateRequest {
    private UUID orderId;
    private String serviceType;
    private BigDecimal cost;
    private String description;
}