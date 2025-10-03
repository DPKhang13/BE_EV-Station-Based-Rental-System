package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.vehicle.VehicleListRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleListResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.services.vehicle.VehicleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicle API", description = "Quản lí xe (create, read, cập nhật, xóa")

public class VehicleController {
    @Autowired
    private VehicleService vehicleService;

    // 1. Tạo sản phẩm (POST)
    @PostMapping("/create")
    public ResponseEntity<VehicleResponse> createVehicle(@RequestBody VehicleRequest vehicleRequest) {
        VehicleResponse vehicleResponse = vehicleService.createVehicle(vehicleRequest);
        return ResponseEntity.status(201).body(vehicleResponse);
    }

    // 2. Lấy tất cả sản phẩm (GET)
    @GetMapping
    public ResponseEntity<VehicleListResponse> getAllVehicles(@ModelAttribute VehicleListRequest vehicleListRequest) {
        VehicleListResponse response = vehicleService.getAllVehicles(vehicleListRequest);
        return ResponseEntity.ok(response);
    }

    // 3. Cập nhật sản phẩm (PUT)
    @PutMapping("/{vehicleId}/update")
    public ResponseEntity<VehicleResponse> updateVehicle(@PathVariable Long vehicleId, @RequestBody VehicleRequest vehicleRequest) {
        VehicleResponse vehicleResponse = vehicleService.updateVehicle(vehicleId, vehicleRequest);
        return ResponseEntity.ok(vehicleResponse);
    }

    // 4. Xóa sản phẩm (DELETE)
    @DeleteMapping("/{vehicleId}/delete")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long vehicleId) {
        vehicleService.deleteVehicle(vehicleId);
        return ResponseEntity.noContent().build();
    }
}
