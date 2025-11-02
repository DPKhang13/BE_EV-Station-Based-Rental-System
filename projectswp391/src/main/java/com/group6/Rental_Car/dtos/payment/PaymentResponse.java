package com.group6.Rental_Car.dtos.payment;

import com.group6.Rental_Car.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private UUID paymentId;
    private UUID orderId;
    private BigDecimal amount;
    private String method;
    private PaymentStatus status;
    private String message;
    private String paymentUrl;
    private short paymentType;
}
