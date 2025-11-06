package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.order.OrderCreateRequest;
import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.dtos.verifyfile.OrderVerificationResponse;
import com.group6.Rental_Car.services.order.RentalOrderService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/order")
@Tag(name = "Api Order", description = "Create, update, delete, pickup, return, get orders")
@RequiredArgsConstructor
public class OrderController {

    private final RentalOrderService rentalOrderService;
    @PostMapping("/create")
    public ResponseEntity<OrderResponse> create(@RequestBody OrderCreateRequest request) {
        OrderResponse response = rentalOrderService.createOrder(request);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/getAll")
    public ResponseEntity<List<OrderResponse>> getAll() {
        List<OrderResponse> orders = rentalOrderService.getRentalOrders();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/get/my-orders")
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal JwtUserDetails userDetails) {
        UUID customerId = userDetails.getUserId();
        List<OrderResponse> orders = rentalOrderService.findByCustomer_UserIdOrderByCreatedAtDesc(customerId);
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/update/{orderId}")
    public ResponseEntity<OrderResponse> update(
            @PathVariable UUID orderId,
            @RequestBody OrderUpdateRequest request
    ) {
        OrderResponse response = rentalOrderService.updateOrder(orderId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete/{orderId}")
    public ResponseEntity<String> delete(@PathVariable UUID orderId) {
        rentalOrderService.deleteOrder(orderId);
        return ResponseEntity.ok("Deleted order successfully");
    }

    @PostMapping("/{orderId}/pickup")
    public ResponseEntity<OrderResponse> confirmPickup(@PathVariable UUID orderId) {
        OrderResponse response = rentalOrderService.confirmPickup(orderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/return")
    public ResponseEntity<OrderResponse> confirmReturn(@PathVariable UUID orderId) {

        OrderResponse response = rentalOrderService.confirmReturn(orderId, null);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/pending-verification")
    public List<OrderVerificationResponse> getPendingVerificationOrders() {
        return rentalOrderService.getPendingVerificationOrders();
    }
}
