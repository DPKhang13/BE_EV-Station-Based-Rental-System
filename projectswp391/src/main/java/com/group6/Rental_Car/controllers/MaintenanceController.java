package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.maintenance.MaintenanceCreateRequest;
import com.group6.Rental_Car.dtos.maintenance.MaintenanceResponse;
import com.group6.Rental_Car.dtos.maintenance.MaintenanceUpdateRequest;
import com.group6.Rental_Car.services.maintenance.MaintenanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Maintenance Api", description ="rating, comments about service")
@RequestMapping("/api/maintanences")
public class MaintenanceController {
    private final MaintenanceService maintenanceService;
    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody MaintenanceCreateRequest req) {
        return ResponseEntity.ok(maintenanceService.create(req));
    }

    @PutMapping("/update/{maintenanceId}")
    public ResponseEntity<?> update(@PathVariable Integer maintenanceId, @RequestBody MaintenanceUpdateRequest req) {
        return ResponseEntity.ok(maintenanceService.update(maintenanceId, req));
    }

    @DeleteMapping("/delete/{maintenanceId}")
    public ResponseEntity<?> delete(@PathVariable Integer maintenanceId) {
        return ResponseEntity.ok("Deleted order successfully");
    }

    @GetMapping("/getById/{maintenanceId}")
    public ResponseEntity<?> getById(@PathVariable Integer maintenanceId) {
        return ResponseEntity.ok(maintenanceService.getById(maintenanceId));
    }
    @GetMapping("/getAllList")
    public ResponseEntity<List<MaintenanceResponse>> getAll() {
        return ResponseEntity.ok(maintenanceService.listAll());
    }
}
