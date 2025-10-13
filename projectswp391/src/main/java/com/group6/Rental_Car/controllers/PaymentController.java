package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.VNPayDto;
import com.group6.Rental_Car.services.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/url")
    public ResponseEntity<?> createPaymentUrl(@RequestParam UUID orderId, @RequestBody PaymentDto paymentDto) {
        return new ResponseEntity<>(paymentService.createPayment(paymentDto, orderId), HttpStatus.OK);
    }

    @GetMapping("/vnpay-callback")
    public ResponseEntity<?> vnPayCallback(VNPayDto vnPayDto) {
        return ResponseEntity.ok(paymentService.handleVNPayCallback(vnPayDto));
    }
}
