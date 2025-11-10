package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.orderdetail.OrderDetailCreateRequest;
import com.group6.Rental_Car.dtos.orderdetail.OrderDetailResponse;
import com.group6.Rental_Car.services.orderdetails.RentalOrderDetailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/order-details")
@RequiredArgsConstructor
@Tag(name = "Order Detail API", description = "Quản lý chi tiết đơn thuê (rentalorder_detail)")
public class RentalOrderDetailController {

    private final RentalOrderDetailService rentalOrderDetailService;


    @PostMapping
    public ResponseEntity<OrderDetailResponse> createOrderDetail(
            @RequestBody OrderDetailCreateRequest request
    ) {
        OrderDetailResponse response = rentalOrderDetailService.createDetail(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{detailId}")
    public ResponseEntity<OrderDetailResponse> updateOrderDetail(
            @PathVariable Long detailId,
            @RequestBody OrderDetailCreateRequest request
    ) {
        OrderDetailResponse response = rentalOrderDetailService.updateDetail(detailId, request);
        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/{detailId}")
    public ResponseEntity<Void> deleteOrderDetail(@PathVariable Long detailId) {
        rentalOrderDetailService.deleteDetail(detailId);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<OrderDetailResponse>> getDetailsByOrder(@PathVariable UUID orderId) {
        List<OrderDetailResponse> responseList = rentalOrderDetailService.getDetailsByOrder(orderId);
        return ResponseEntity.ok(responseList);
    }


    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<OrderDetailResponse>> getDetailsByVehicle(@PathVariable Long vehicleId) {
        List<OrderDetailResponse> responseList = rentalOrderDetailService.getDetailsByVehicle(vehicleId);
        return ResponseEntity.ok(responseList);
    }


    @GetMapping("/vehicle/{vehicleId}/active")
    public ResponseEntity<List<OrderDetailResponse>> getActiveDetailsByVehicle(@PathVariable Long vehicleId) {
        List<OrderDetailResponse> responseList = rentalOrderDetailService.getActiveDetailsByVehicle(vehicleId);
        return ResponseEntity.ok(responseList);
    }


    @GetMapping("/order/{orderId}/active")
    public ResponseEntity<List<OrderDetailResponse>> getActiveDetailsByOrder(@PathVariable UUID orderId) {
        List<OrderDetailResponse> responseList = rentalOrderDetailService.getActiveDetailsByOrder(orderId);
        return ResponseEntity.ok(responseList);
    }
}
