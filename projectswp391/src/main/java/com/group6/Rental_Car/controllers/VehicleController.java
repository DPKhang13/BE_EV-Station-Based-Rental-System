package com.group6.Rental_Car.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleDetailResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateStatusRequest;
import com.group6.Rental_Car.services.vehicle.VehicleService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicle Api", description ="CRUD về xe")
public class VehicleController {
    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestPart(value = "vehicle", required = false) String vehicleJson,
            @ModelAttribute VehicleCreateRequest reqModel,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal JwtUserDetails userDetails) throws IOException {

        VehicleCreateRequest req;

        if (StringUtils.hasText(vehicleJson)) {
            System.out.println("[VehicleController] Received JSON string: " + vehicleJson);
            try {
                req = objectMapper.readValue(vehicleJson, VehicleCreateRequest.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON format in 'vehicle' part: " + e.getMessage());
            }
        }
        // Nếu frontend gửi form data thông thường
        else if (reqModel != null && reqModel.getPlateNumber() != null) {
            System.out.println("[VehicleController] Received form data");
            req = reqModel;
        }
        else {
            throw new IllegalArgumentException("Vehicle data is required. Send either 'vehicle' (JSON string) or form fields.");
        }
        VehicleResponse response = vehicleService.createVehicle(req, images);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/get")
    public ResponseEntity<List<?>> getVehicleById() {
        List<VehicleResponse> vehicles = vehicleService.getAllVehicles();
        return ResponseEntity.ok(vehicles);
    }

    @GetMapping("/{vehicleId}")
    public ResponseEntity<VehicleResponse> getVehicleById(@PathVariable Long vehicleId) {
        VehicleResponse vehicle = vehicleService.getVehicleById(vehicleId);
        return ResponseEntity.ok(vehicle);
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

    @GetMapping("/station/{stationId}/available")
    public ResponseEntity<List<VehicleResponse>> getAvailableVehiclesByStation(
            @PathVariable Integer stationId,
            @RequestParam(required = false) String carmodel) {
        List<VehicleResponse> vehicles = vehicleService.getAvailableVehiclesByStation(stationId, carmodel);
        return ResponseEntity.ok(vehicles);
    }

    @GetMapping("/carmodel/{carmodel}")
    public ResponseEntity<List<VehicleResponse>> getVehiclesByCarmodel(
            @PathVariable String carmodel) {
        List<VehicleResponse> vehicles = vehicleService.getVehiclesByCarmodel(carmodel);
        return ResponseEntity.ok(vehicles);
    }

    @GetMapping("/available")
    public ResponseEntity<List<VehicleResponse>> getAvailableVehicles(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime,
            @RequestParam(required = false) Integer stationId) {
        List<VehicleResponse> vehicles = vehicleService.getAvailableVehicles(startTime, endTime, stationId);
        return ResponseEntity.ok(vehicles);
    }

    @GetMapping("/{vehicleId}/similar")
    public ResponseEntity<List<VehicleResponse>> getSimilarAvailableVehicles(
            @PathVariable Long vehicleId) {
        List<VehicleResponse> vehicles = vehicleService.getSimilarAvailableVehicles(vehicleId);
        return ResponseEntity.ok(vehicles);
    }

}