package com.group6.Rental_Car.dtos.payment;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDto {
    private String method;      // VNPay, Cash, Card...
    private String clientIp;    // IP client (127.0.0.1)
    private String orderInfo;   // Mô tả đơn hàng
    private boolean deposit;    // true = đặt cọc 50%
}
