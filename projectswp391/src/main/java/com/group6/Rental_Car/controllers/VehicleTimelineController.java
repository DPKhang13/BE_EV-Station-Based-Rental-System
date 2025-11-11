package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.timeline.VehicleTimelineCreateRequest;
import com.group6.Rental_Car.dtos.timeline.VehicleTimelineDto;
import com.group6.Rental_Car.services.timeline.VehicleTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/vehicle-timelines")
@RequiredArgsConstructor
public class VehicleTimelineController {

    private final VehicleTimelineService vehicleTimelineService;

    @PostMapping
    public ResponseEntity<VehicleTimelineDto> create(@RequestBody VehicleTimelineCreateRequest request) {
        return ResponseEntity.ok(vehicleTimelineService.create(request));
    }

    @GetMapping("/{vehicleId}")
    public ResponseEntity<List<VehicleTimelineDto>> getByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehicleTimelineService.getByVehicle(vehicleId));
    }

    @GetMapping("/{vehicleId}/range")
    public ResponseEntity<List<VehicleTimelineDto>> getByDateRange(
            @PathVariable Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(vehicleTimelineService.getByVehicleAndDateRange(vehicleId, from, to));
    }
}
