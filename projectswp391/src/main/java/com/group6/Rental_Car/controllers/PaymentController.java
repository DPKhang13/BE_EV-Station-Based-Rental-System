package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;
import com.group6.Rental_Car.services.payment.PaymentService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/url")
    public ResponseEntity<?> createUrl(@RequestBody PaymentDto paymentDto,
                                       @AuthenticationPrincipal JwtUserDetails jwtUserDetails) {

        return new ResponseEntity<>(paymentService.createPaymentUrl(paymentDto, jwtUserDetails.getUserId()), HttpStatus.OK);
    }

    @PostMapping("/refund/{orderId}")
    public ResponseEntity<PaymentResponse> refund(@PathVariable UUID orderId) {
        PaymentResponse response = paymentService.refund(orderId);
        return ResponseEntity.ok(response);
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
    @PostMapping("/verify-vnpay")
    public ResponseEntity<PaymentResponse> verifyVNPayPayment(
            @RequestBody Map<String, String> vnpParams) {

        log.info("[VNPay Verify] Received params: {}", vnpParams);

        try {
            String responseCode = vnpParams.get("vnp_ResponseCode");
            log.info("[VNPay Verify] Response Code: {}", responseCode);

            PaymentResponse result = paymentService.handleVNPayCallback(vnpParams);

            log.info("[VNPay Verify] Success: {}", result);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[VNPay Verify] Error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(PaymentResponse.builder()
                            .message("PAYMENT_FAILED")
                            .build());
        }
    }
}
