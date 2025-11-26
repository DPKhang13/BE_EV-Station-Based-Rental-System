package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.order.*;
import com.group6.Rental_Car.dtos.verifyfile.OrderVerificationResponse;
import com.group6.Rental_Car.services.order.RentalOrderService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @GetMapping("/getAll/simple")
    public ResponseEntity<List<OrderSimpleResponse>> getAllSimple() {
        List<OrderSimpleResponse> orders = rentalOrderService.getRentalOrdersSimple();
        return ResponseEntity.ok(orders);
    }

 
    @GetMapping("/get/my-orders")
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal JwtUserDetails userDetails) {
        UUID customerId = userDetails.getUserId();
        List<OrderResponse> orders = rentalOrderService.findByCustomer_UserId(customerId);
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

    @PutMapping("/{orderId}/change-vehicle")
    public ResponseEntity<OrderResponse> changeVehicle(
            @PathVariable UUID orderId,
            @Valid @RequestBody ChangeVehicleRequest request
    ) {
        OrderResponse response = rentalOrderService.changeVehicle(
                orderId,
                request.getNewVehicleId(),
                request.getNote()
        );
        return ResponseEntity.ok(response);
    }

    @PutMapping("/cancel/{orderId}")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable UUID orderId,
            @RequestBody(required = false) CancelOrderRequest request) {
        String cancellationReason = request != null ? request.getCancellationReason() : null;
        OrderResponse response = rentalOrderService.cancelOrder(orderId, cancellationReason);
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
    public ResponseEntity<OrderResponse> confirmReturn(
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderReturnRequest request) {

        OrderResponse response = rentalOrderService.confirmReturn(orderId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}/review-return")
    public ResponseEntity<OrderResponse> reviewReturn(@PathVariable UUID orderId) {
        OrderResponse response = rentalOrderService.reviewReturn(orderId);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/pending-verification")
    public List<OrderVerificationResponse> getPendingVerificationOrders() {
        return rentalOrderService.getPendingVerificationOrders();
    }
    @GetMapping("/vehicle/{vehicleId}/history")
    public ResponseEntity<List<VehicleOrderHistoryResponse>> getVehicleOrderHistory(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(rentalOrderService.getOrderHistoryByVehicle(vehicleId));
    }
    @GetMapping("/customer/{customerId}/history")
    public ResponseEntity<List<VehicleOrderHistoryResponse>> getCustomerOrderHistory(@PathVariable UUID customerId) {
        return ResponseEntity.ok(rentalOrderService.getOrderHistoryByCustomer(customerId));
    }
    @GetMapping("/vehicle/{vehicleId}/compact")
    public ResponseEntity<List<OrderDetailCompactResponse>> getCompactByVehicle(
            @PathVariable Long vehicleId
    ) {
        return ResponseEntity.ok(rentalOrderService.getCompactDetailsByVehicle(vehicleId));
    }
    @PutMapping("/vehicle/{vehicleId}/{orderId}/compact")
    public ResponseEntity<?> updateCompactOrder(
            @PathVariable Long vehicleId,
            @PathVariable UUID orderId,
            @RequestBody CompactOrderUpdateRequest req
    ) {
        return ResponseEntity.ok(rentalOrderService.updateCompactOrder(vehicleId, orderId, req));
    }

    @PutMapping("/{orderId}/complete")
    public ResponseEntity<OrderResponse> completeOrder(@PathVariable UUID orderId) {
        OrderResponse response = rentalOrderService.completeOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
