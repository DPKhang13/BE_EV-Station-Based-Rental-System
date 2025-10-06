package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.vehicleAttribute.BulkUpsertRequest;
import com.group6.Rental_Car.dtos.vehicleAttribute.BulkUpsertResult;
import com.group6.Rental_Car.dtos.vehicleAttribute.VehicleAttributeDTO;
import com.group6.Rental_Car.dtos.vehicleAttribute.VehicleAttributeRequest;
import com.group6.Rental_Car.services.VehicleAttribute.VehicleAttributeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/vehicle-attributes")
@RequiredArgsConstructor
public class VehicleAttributeController {

    private final VehicleAttributeService service;

    @GetMapping("/list")
    public ResponseEntity<Page<VehicleAttributeDTO>> list(
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) String plate,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "plateNumber,asc,attrName,asc") String sort
    ) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<VehicleAttributeDTO> result = service.list(vehicleId, plate, q, pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<VehicleAttributeDTO> create(
            @Valid @RequestBody VehicleAttributeRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VehicleAttributeDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody VehicleAttributeRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/bulk-upsert")
    public ResponseEntity<BulkUpsertResult> bulkUpsert(@Valid @RequestBody BulkUpsertRequest req) {
        return ResponseEntity.ok(service.bulkUpsert(req));
    }

    private Pageable buildPageable(int page, int size, String sort) {
        // ví dụ sort="plateNumber,asc,attrName,asc" hoặc "attrName,desc"
        String[] parts = sort.split(",");
        Sort s = Sort.unsorted();
        for (int i = 0; i < parts.length; i += 2) {
            String prop = parts[i].trim();
            String dir = (i + 1 < parts.length) ? parts[i + 1].trim() : "asc";
            Sort next = "desc".equalsIgnoreCase(dir) ? Sort.by(prop).descending() : Sort.by(prop).ascending();
            s = (s.isUnsorted()) ? next : s.and(next);
        }
        return PageRequest.of(page, size, s);
    }
}
