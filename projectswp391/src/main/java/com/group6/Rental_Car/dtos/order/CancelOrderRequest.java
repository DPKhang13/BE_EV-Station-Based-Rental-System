package com.group6.Rental_Car.dtos.order;

import lombok.Data;

@Data
public class CancelOrderRequest {
    private String cancellationReason; // Lý do hủy đơn
}

