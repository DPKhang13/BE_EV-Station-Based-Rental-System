package com.group6.Rental_Car.dtos.order;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrderDetailCompactResponse {

    private UUID orderId;

    private BigDecimal price;
    private String status;

    private String customerName;
    private String customerPhone;
    private String stationName;

    private LocalDateTime createdAt;
}

