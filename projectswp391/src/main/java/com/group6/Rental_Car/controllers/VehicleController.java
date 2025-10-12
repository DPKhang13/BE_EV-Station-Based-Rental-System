package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.services.vehicle.VehicleService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicle Api", description ="create,update,deleted,getAll,getById")
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
    @PutMapping("/update/{vehicleId}")
    public ResponseEntity<?> updateVehicle(@PathVariable Long vehicleId,
                                           @RequestBody VehicleUpdateRequest req,
                                           @AuthenticationPrincipal JwtUserDetails userDetails) {
        VehicleResponse response = vehicleService.updateVehicle(vehicleId, req);
        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/deleted/{vehicleId}")
    public ResponseEntity<?> deleteVehicle(@PathVariable Long vehicleId,
                                           @AuthenticationPrincipal JwtUserDetails userDetails) {
        vehicleService.deleteVehicle(vehicleId);
        return ResponseEntity.ok("Vehicle deleted successfully");
    }
}
