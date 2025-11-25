package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.orderservice.OrderServiceCreateRequest;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;
import com.group6.Rental_Car.dtos.orderservice.ServicePriceCreateRequest;
import com.group6.Rental_Car.services.orderservice.OrderServiceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/order-services")
@RequiredArgsConstructor
@Tag(name = "Order Service API", description = "Quản lý các dịch vụ đi kèm / bảo dưỡng / sự cố của đơn thuê")
public class OrderServiceController {

    private final OrderServiceService orderServiceService;

    @PostMapping
    public ResponseEntity<OrderServiceResponse> create(@RequestBody OrderServiceCreateRequest request) {
        return ResponseEntity.ok(orderServiceService.createService(request));
    }

    @PostMapping("/price")
    public ResponseEntity<OrderServiceResponse> createServicePrice(@RequestBody ServicePriceCreateRequest request) {
        return ResponseEntity.ok(orderServiceService.createServicePrice(request));
    }

    @PutMapping("/{serviceId}")
    public ResponseEntity<OrderServiceResponse> update(
            @PathVariable Long serviceId,
            @RequestBody OrderServiceCreateRequest request
    ) {
        return ResponseEntity.ok(orderServiceService.updateService(serviceId, request));
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> delete(@PathVariable Long serviceId) {
        orderServiceService.deleteService(serviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<OrderServiceResponse>> getByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderServiceService.getServicesByOrder(orderId));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<OrderServiceResponse>> getByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(orderServiceService.getServicesByVehicle(vehicleId));
    }

    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<OrderServiceResponse>> getByStation(@PathVariable Integer stationId) {
        return ResponseEntity.ok(orderServiceService.getServicesByStation(stationId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderServiceResponse>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(orderServiceService.getServicesByStatus(status));
    }

    @GetMapping("/price-list")
    public ResponseEntity<List<OrderServiceResponse>> getPriceList() {
        return ResponseEntity.ok(orderServiceService.getPriceList());
    }
}
