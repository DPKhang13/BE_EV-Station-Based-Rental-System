package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleDetailResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateStatusRequest;
import com.group6.Rental_Car.services.vehicle.VehicleService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicle Api", description ="CRUD về xe")
public class VehicleController {
    @Autowired
    private VehicleService vehicleService;



    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody VehicleCreateRequest req,
                                    @AuthenticationPrincipal JwtUserDetails userDetails) {
        VehicleResponse response = vehicleService.createVehicle(req);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/get")
    public ResponseEntity<List<?>> getVehicleById() {
        List<VehicleResponse> vehicles = vehicleService.getAllVehicles();
        return ResponseEntity.ok(vehicles);
    }

    @GetMapping("/{vehicleId}/detail")
    public ResponseEntity<VehicleDetailResponse> getVehicleDetail(@PathVariable Long vehicleId) {
        VehicleDetailResponse detail = vehicleService.getVehicleDetailById(vehicleId);
        return ResponseEntity.ok(detail);
    }
    @PutMapping("/update/{vehicleId}")
    public ResponseEntity<?> updateVehicle(@PathVariable Long vehicleId,
                                           @RequestBody VehicleUpdateRequest req,
                                           @AuthenticationPrincipal JwtUserDetails userDetails) {
        VehicleResponse response = vehicleService.updateVehicle(vehicleId, req);
        return ResponseEntity.ok(response);
    }
    @PutMapping("/updateStatus/{vehicleId}")
    public ResponseEntity<?> updateStatusVehicle(@PathVariable Long vehicleId,
                                                 @RequestBody VehicleUpdateStatusRequest req,
                                                 @AuthenticationPrincipal JwtUserDetails userDetails) {
        VehicleResponse response = vehicleService.updateStatusVehicle(vehicleId, req);
        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/deleted/{vehicleId}")
    public ResponseEntity<?> deleteVehicle(@PathVariable Long vehicleId,
                                           @AuthenticationPrincipal JwtUserDetails userDetails) {
        vehicleService.deleteVehicle(vehicleId);
        return ResponseEntity.ok("Vehicle deleted successfully");
    }

    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<VehicleResponse>> getVehiclesByStation(
            @PathVariable Integer stationId) {
        List<VehicleResponse> vehicles = vehicleService.getVehiclesByStation(stationId);
        return ResponseEntity.ok(vehicles);
    }

    @GetMapping("/carmodel/{carmodel}")
    public ResponseEntity<List<VehicleResponse>> getVehiclesByCarmodel(
            @PathVariable String carmodel) {
        List<VehicleResponse> vehicles = vehicleService.getVehiclesByCarmodel(carmodel);
        return ResponseEntity.ok(vehicles);
    }

}