package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.rentalorderdetail.RentalOrderDetailCreateRequest;
import com.group6.Rental_Car.dtos.rentalorderdetail.RentalOrderDetailResponse;
import com.group6.Rental_Car.dtos.rentalorderdetail.RentalOrderDetailUpdateRequest;
import com.group6.Rental_Car.services.rentalorderdetail.RentalOrderDetailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/Rental Order Detail")
@Tag(name = "Api Rental Order Detail", description = "CRUD Rental Order Detail")
public class RentalOrderDetail {
    private final RentalOrderDetailService rentalOrderDetailService;

    @PostMapping
    public ResponseEntity<RentalOrderDetailResponse> create(@RequestBody RentalOrderDetailCreateRequest req) {
        return ResponseEntity.ok(rentalOrderDetailService.create(req));
    }

    @PutMapping("/{detailId}")
    public ResponseEntity<RentalOrderDetailResponse> update(@PathVariable Long detailId,
                                                            @RequestBody RentalOrderDetailUpdateRequest req) {
        return ResponseEntity.ok(rentalOrderDetailService.update(detailId, req));
    }

    @DeleteMapping("/{detailId}")
    public ResponseEntity<Void> delete(@PathVariable Long detailId) {
        rentalOrderDetailService.delete(detailId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{detailId}")
    public ResponseEntity<RentalOrderDetailResponse> getById(@PathVariable Long detailId) {
        return ResponseEntity.ok(rentalOrderDetailService.getById(detailId));
    }

    @GetMapping
    public ResponseEntity<List<RentalOrderDetailResponse>> listAll() {
        return ResponseEntity.ok(rentalOrderDetailService.listAll());
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<RentalOrderDetailResponse>> listByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(rentalOrderDetailService.listByOrder(orderId));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<RentalOrderDetailResponse>> listByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(rentalOrderDetailService.listByVehicle(vehicleId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<RentalOrderDetailResponse>> listByStatus(@PathVariable String status) {
        return ResponseEntity.ok(rentalOrderDetailService.listByStatus(status));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<RentalOrderDetailResponse>> listByType(@PathVariable String type) {
        return ResponseEntity.ok(rentalOrderDetailService.listByType(type));
    }
}
