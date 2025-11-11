package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.orderservice.OrderServiceCreateRequest;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceUpdateRequest;
import com.group6.Rental_Car.services.orderservice.OrderServiceManager;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Order Service Api", description ="CRUD về bảo dưỡng liên quan đến xe")
@RequestMapping("/api/OrderService")
@RequiredArgsConstructor
public class OrderServiceController {
    private final OrderServiceManager orderService;

    @PostMapping("/create")
    public ResponseEntity<OrderServiceResponse> create(@RequestBody OrderServiceCreateRequest req) {
        return ResponseEntity.ok(orderService.create(req));
    }

    @PutMapping("/update/{serviceId}")
    public ResponseEntity<OrderServiceResponse> update(@PathVariable Integer serviceId, @RequestBody OrderServiceUpdateRequest req) {
        return ResponseEntity.ok(orderService.update(serviceId, req));
    }

    @DeleteMapping("/delete/{serviceId}")
    public ResponseEntity<?> delete(@PathVariable Integer serviceId) {
        orderService.delete(serviceId);
        return ResponseEntity.ok("Deleted incident successfully");
    }

    @GetMapping("/getById/{serviceId}")
    public ResponseEntity<OrderServiceResponse> getById(@PathVariable Integer serviceId) {
        return ResponseEntity.ok(orderService.getById(serviceId));
    }
    @GetMapping("/getAllList")
    public ResponseEntity<List<OrderServiceResponse>> getAll() {
        return ResponseEntity.ok(orderService.listAll());
    }
}
