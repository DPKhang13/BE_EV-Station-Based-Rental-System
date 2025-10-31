package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse createPaymentUrl(PaymentDto paymentDto, UUID userId);
    PaymentResponse handleVNPayCallback(Map<String, String> vnpParams);
    PaymentResponse refund(UUID orderId);
}
