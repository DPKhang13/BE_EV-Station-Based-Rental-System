package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;
import com.group6.Rental_Car.services.payment.PaymentService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/url")
    public ResponseEntity<?> createUrl(@RequestBody PaymentDto paymentDto,
                                              @AuthenticationPrincipal JwtUserDetails jwtUserDetails) {

        return new ResponseEntity<>(paymentService.createPaymentUrl(paymentDto, jwtUserDetails.getUserId()), HttpStatus.OK);
    }

    @GetMapping("/vnpay-callback")
    @Operation(summary = "VNPay callback")
    public ResponseEntity<?> vnpayCallback(
            @RequestParam String vnp_TxnRef,
            @RequestParam String vnp_Amount,
            @RequestParam String vnp_ResponseCode,
            @RequestParam String vnp_SecureHash) {
        Map<String, String> vnpParams = Map.of(
                "vnp_TxnRef", vnp_TxnRef,
                "vnp_Amount", vnp_Amount,
                "vnp_ResponseCode", vnp_ResponseCode,
                "vnp_SecureHash", vnp_SecureHash
        );
        return ResponseEntity.ok(paymentService.handleVNPayCallback(vnpParams));
    }
    @PostMapping("/refund/{orderId}")
    public ResponseEntity<PaymentResponse> refund(@PathVariable UUID orderId) {
        PaymentResponse response = paymentService.refund(orderId);
        return ResponseEntity.ok(response);
    }
}
