package com.group6.Rental_Car.dtos.order;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompactOrderUpdateRequest {
    private String status;
    private BigDecimal price;
    private String stationName;
}
