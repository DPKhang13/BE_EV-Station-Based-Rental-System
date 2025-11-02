package com.group6.Rental_Car.dtos.payment;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDto {
    private UUID orderId;
    private String method;      // VNPay, Cash, Card...
    private Short paymentType;
}
