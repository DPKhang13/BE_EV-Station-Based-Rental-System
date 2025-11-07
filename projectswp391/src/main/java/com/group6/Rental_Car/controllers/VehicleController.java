package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
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

    /**
     * Endpoint đơn giản để lấy URL ảnh dựa trên brand và color
     * Frontend có thể dùng endpoint này để lấy URL ảnh khi chọn brand/color
     */
    @GetMapping("/image-url")
    @CrossOrigin(
            origins = "http://localhost:5173",
            allowCredentials = "true"
    )
    public ResponseEntity<?> getImageUrl(
            @RequestParam(required = false) Integer seatCount,
            @RequestParam String brand,
            @RequestParam String color) {

        String baseUrl = "https://s3-hcm5-r1.longvan.net/19430189-verify-customer-docs/imgCar";
        String imageUrl = null;

        // Normalize inputs
        String normalizedBrand = brand.trim();
        String normalizedColor = color.trim().toLowerCase();
        int seat = (seatCount != null) ? seatCount : 4; // Default 4 chỗ

        // Build URL based on brand and color
        if ("Vinfast".equalsIgnoreCase(normalizedBrand)) {
            switch (normalizedColor) {
                case "xanh":
                case "blue":
                    imageUrl = baseUrl + "/4_Cho/Vinfast/a80cae76-5c8a-4226-ac85-116ba2da7a3a.png";
                    break;
                case "bạc":
                case "silver":
                    imageUrl = baseUrl + "/4_Cho/Vinfast/b76c51c2-6e69-491c-ae83-0d36ff93cdff.png";
                    break;
                case "đen":
                case "black":
                    imageUrl = baseUrl + "/4_Cho/Vinfast/e88bd242-3df4-48a7-8fe2-a9a3466f939f.png";
                    break;
                case "đỏ":
                case "red":
                    imageUrl = baseUrl + "/4_Cho/Vinfast/e420cb1b-1710-4dbe-a5e3-e1285c690b6e.png";
                    break;
                case "trắng":
                case "white":
                    imageUrl = baseUrl + "/4_Cho/Vinfast/unnamed.jpg";
                    break;
            }
        } else if ("BMW".equalsIgnoreCase(normalizedBrand)) {
            switch (normalizedColor) {
                case "trắng":
                case "white":
                    imageUrl = baseUrl + "/4_Cho/BMW/white.jpg";
                    break;
                case "bạc":
                case "silver":
                    imageUrl = baseUrl + "/4_Cho/BMW/unnamed%20%281%29.jpg";
                    break;
                case "xanh":
                case "blue":
                    imageUrl = baseUrl + "/4_Cho/BMW/blue.jpg";
                    break;
                case "đen":
                case "black":
                    imageUrl = baseUrl + "/4_Cho/BMW/8f9f3e31-0c04-4441-bb40-97778c9824e0.png";
                    break;
                case "đỏ":
                case "red":
                    imageUrl = baseUrl + "/4_Cho/BMW/7f3edc23-30ba-4e84-83a9-c8c418f2362d.png";
                    break;
            }
        } else if ("Tesla".equalsIgnoreCase(normalizedBrand)) {
            switch (normalizedColor) {
                case "bạc":
                case "silver":
                    imageUrl = baseUrl + "/4_Cho/Tesla/unnamed4.jpg";
                    break;
                case "xanh":
                case "blue":
                    imageUrl = baseUrl + "/4_Cho/Tesla/unnamed.jpg";
                    break;
                case "đen":
                case "black":
                    imageUrl = baseUrl + "/4_Cho/Tesla/unnamed%20%283%29.jpg";
                    break;
                case "trắng":
                case "white":
                    imageUrl = baseUrl + "/4_Cho/Tesla/unnamed%20%282%29.jpg";
                    break;
                case "đỏ":
                case "red":
                    imageUrl = baseUrl + "/4_Cho/Tesla/unnamed%20%281%29.jpg";
                    break;
            }
        }

        if (imageUrl == null) {
            return ResponseEntity.status(404)
                    .body(java.util.Map.of(
                            "error", "Image not found",
                            "message", "No image found for brand: " + brand + ", color: " + color
                    ));
        }

        return ResponseEntity.ok(java.util.Map.of("imageUrl", imageUrl));
    }

}