package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.incident.IncidentCreateRequest;
import com.group6.Rental_Car.dtos.incident.IncidentResponse;
import com.group6.Rental_Car.dtos.incident.IncidentUpdateRequest;
import com.group6.Rental_Car.services.incident.IncidentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Incident Api", description ="CRUD về sự cố liên quan đến xe")
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentService incidentService;
    public IncidentController(IncidentService maintenanceService) {
        this.incidentService = maintenanceService;
    }

    @PostMapping("/create")
    public ResponseEntity<IncidentResponse> create(@RequestBody IncidentCreateRequest req) {
        return ResponseEntity.ok(incidentService.create(req));
    }

    @PutMapping("/update/{incidentId}")
    public ResponseEntity<IncidentResponse> update(@PathVariable Integer incidentId, @RequestBody IncidentUpdateRequest req) {
        return ResponseEntity.ok(incidentService.update(incidentId, req));
    }

    @DeleteMapping("/delete/{incidentId}")
    public ResponseEntity<?> delete(@PathVariable Integer incidentId) {
        incidentService.delete(incidentId);
        return ResponseEntity.ok("Deleted maintenance successfully");
    }

    @GetMapping("/getById/{incidentId}")
    public ResponseEntity<IncidentResponse> getById(@PathVariable Integer incidentId) {
        return ResponseEntity.ok(incidentService.getById(incidentId));
    }
    @GetMapping("/getAllList")
    public ResponseEntity<List<IncidentResponse>> getAll() {
        return ResponseEntity.ok(incidentService.listAll());
    }
}
