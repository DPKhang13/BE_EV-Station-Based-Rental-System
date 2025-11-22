package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.incidentreport.IncidentReportCreateRequest;
import com.group6.Rental_Car.dtos.incidentreport.IncidentReportResponse;
import com.group6.Rental_Car.dtos.incidentreport.IncidentReportUpdateRequest;
import com.group6.Rental_Car.services.incidentreport.IncidentReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Incident Report Api", description = "Báo cáo sự cố xe")
@RequestMapping("/api/incident-reports")
public class IncidentReportController {
    private final IncidentReportService incidentReportService;

    public IncidentReportController(IncidentReportService incidentReportService) {
        this.incidentReportService = incidentReportService;
    }

    @PostMapping("/create")
    public ResponseEntity<IncidentReportResponse> create(@RequestBody IncidentReportCreateRequest req) {
        return ResponseEntity.ok(incidentReportService.create(req));
    }

    @PutMapping("/update/{incidentId}")
    public ResponseEntity<IncidentReportResponse> update(
            @PathVariable Integer incidentId,
            @RequestBody IncidentReportUpdateRequest req) {
        return ResponseEntity.ok(incidentReportService.update(incidentId, req));
    }

    @DeleteMapping("/delete/{incidentId}")
    public ResponseEntity<Void> delete(@PathVariable Integer incidentId) {
        incidentReportService.delete(incidentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{incidentId}")
    public ResponseEntity<IncidentReportResponse> getById(@PathVariable Integer incidentId) {
        return ResponseEntity.ok(incidentReportService.getById(incidentId));
    }

    @GetMapping("/getAll")
    public ResponseEntity<List<IncidentReportResponse>> getAll() {
        return ResponseEntity.ok(incidentReportService.getAll());
    }

    @GetMapping("/getByVehicleId/{vehicleId}")
    public ResponseEntity<List<IncidentReportResponse>> getByVehicleId(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(incidentReportService.getByVehicleId(vehicleId));
    }
}

