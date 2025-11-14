package com.group6.Rental_Car.dtos.payment;

import com.group6.Rental_Car.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID paymentId;
    private UUID orderId;
    private BigDecimal amount;
    private BigDecimal remainingAmount;
    private Short paymentType;
    private String method;
    private PaymentStatus status;
    private String paymentUrl;
    private String qrCodeUrl;
    private String deeplink;
    private String message;
}

