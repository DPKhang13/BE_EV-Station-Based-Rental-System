package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;
import com.group6.Rental_Car.entities.Payment;
import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.PaymentRepository;
import com.group6.Rental_Car.services.payment.PaymentService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment", description = "MoMo Payment API")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

    @PostMapping("/url")
    @Operation(summary = "Create MoMo payment URL")
    public ResponseEntity<PaymentResponse> createPaymentUrl(
            @RequestBody PaymentDto paymentDto,
            @AuthenticationPrincipal JwtUserDetails jwtUserDetails) {

        PaymentResponse response = paymentService.createPaymentUrl(paymentDto, jwtUserDetails.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cash")
    @Operation(summary = "Process cash payment")
    public ResponseEntity<PaymentResponse> processCashPayment(
            @RequestBody PaymentDto paymentDto,
            @AuthenticationPrincipal JwtUserDetails jwtUserDetails) {

        PaymentResponse response = paymentService.processCashPayment(paymentDto, jwtUserDetails.getUserId());
        return ResponseEntity.ok(response);
    }
    @PutMapping("/cash/approve/order/{orderId}")
    @Operation(summary = "Approve cash payment by orderId")
    public ResponseEntity<?> approveCashPaymentByOrder(@PathVariable UUID orderId) {

        paymentService.approveCashPaymentByOrder(orderId);
        return ResponseEntity.ok("CASH payment approved successfully");
    }

    @PostMapping("/refund/{orderId}")
    @Operation(summary = "Refund payment")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable UUID orderId,
            @RequestParam(required = false) BigDecimal amount) {
        PaymentResponse response = paymentService.refund(orderId, amount);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/momo-callback")
    @Operation(summary = "MoMo IPN callback (called by MoMo server)")
    public ResponseEntity<?> momoCallback(@RequestBody Map<String, String> momoParams) {
        log.info("📥 MoMo IPN Callback: {}", momoParams);

        try {
            PaymentResponse response = paymentService.handleMoMoCallback(momoParams);
            log.info("✅ MoMo callback processed successfully: {}", response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ MoMo callback error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "message", "ERROR",
                        "error", e.getMessage()
                    ));
        }
    }

    @GetMapping("/momo-return")
    @Operation(summary = "MoMo return URL (user redirected from MoMo)")
    public void momoReturn(
            @RequestParam Map<String, String> params,
            HttpServletResponse response) throws IOException {

        log.info("🔙 MoMo Return: {}", params);

        String resultCode = params.get("resultCode");
        String orderId = params.get("orderId");

        // Redirect to frontend with result
        String frontendUrl = "http://localhost:5173/payment-callback?resultCode=" + resultCode + "&orderId=" + orderId;
        response.sendRedirect(frontendUrl);
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify MoMo payment from frontend")
    public ResponseEntity<PaymentResponse> verifyPayment(@RequestBody Map<String, String> momoParams) {
        log.info("🔍 [MoMo Verify] Received params: {}", momoParams);

        try {
            PaymentResponse result = paymentService.handleMoMoCallback(momoParams);
            log.info("✅ [MoMo Verify] Success: {}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ [MoMo Verify] Error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(PaymentResponse.builder()
                            .message("PAYMENT_FAILED")
                            .build());
        }
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get all payments for an order")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrderId(@PathVariable UUID orderId) {
        log.info("📋 Getting payments for order: {}", orderId);
        List<PaymentResponse> payments = paymentService.getPaymentsByOrderId(orderId);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/order/{orderId}/refunded-amount")
    @Operation(summary = "Get total refunded amount for an order")
    public ResponseEntity<Map<String, Object>> getRefundedAmountByOrderId(@PathVariable UUID orderId) {
        log.info("💰 Getting refunded amount for order: {}", orderId);
        BigDecimal refundedAmount = paymentService.getRefundedAmountByOrderId(orderId);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "refundedAmount", refundedAmount
        ));
    }

    @GetMapping("/order/{orderId}/refund-reason")
    @Operation(summary = "Get refund reason for an order")
    public ResponseEntity<Map<String, Object>> getRefundReasonByOrderId(@PathVariable UUID orderId) {
        log.info("📝 Getting refund reason for order: {}", orderId);
        String refundReason = paymentService.getRefundReasonByOrderId(orderId);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("orderId", orderId);
        response.put("refundReason", refundReason != null ? refundReason : "");
        return ResponseEntity.ok(response);
    }
}


