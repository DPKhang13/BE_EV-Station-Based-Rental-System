package com.group6.Rental_Car.dtos.orderservice;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderServiceResponse {
    private Long serviceId;
    private String serviceType;
    private String description;
    private BigDecimal cost;
}
