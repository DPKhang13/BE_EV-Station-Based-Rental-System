package com.group6.Rental_Car.dtos.orderservice;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicePriceCreateRequest {
    private String serviceType;
    private BigDecimal cost;
    private String description;
}

