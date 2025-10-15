package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;
import com.group6.Rental_Car.dtos.payment.VNPayDto;

import java.util.UUID;

public interface PaymentService {
    PaymentResponse createPaymentUrl(PaymentDto paymentDto, UUID userId);
}
