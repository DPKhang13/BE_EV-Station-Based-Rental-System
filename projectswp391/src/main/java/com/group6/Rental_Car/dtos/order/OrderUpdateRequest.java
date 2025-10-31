package com.group6.Rental_Car.dtos.order;

import lombok.Data;

import java.util.UUID;
@Data
public class OrderUpdateRequest {
    private String status;
    private Long vehicleId;
    private String couponCode;
}
