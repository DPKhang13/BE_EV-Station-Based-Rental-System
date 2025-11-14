package com.group6.Rental_Car.dtos.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDto {
    private UUID orderId;
    private Short paymentType; // 1: Deposit, 2: Final, 3: Full, 4: Refund, 5: Service
    private String method; // MOMO
}

