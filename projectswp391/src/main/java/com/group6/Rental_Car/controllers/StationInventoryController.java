package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.stationinventory.StationInventoryRequest;
import com.group6.Rental_Car.dtos.stationinventory.StationInventoryResponse;
import com.group6.Rental_Car.dtos.stationinventory.StationInventoryUpdateRequest;
import com.group6.Rental_Car.services.stationinventory.StationInventoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/StationInventory")
@Tag(name = "Api StationInventory", description = "CRUD StationInventory")
public class StationInventoryController {
    private final StationInventoryService stationInventoryService;

    @PostMapping("/create")
    public ResponseEntity<StationInventoryResponse> create(@Valid @RequestBody StationInventoryRequest req) {
        return ResponseEntity.ok(stationInventoryService.create(req));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<StationInventoryResponse> update(@PathVariable Integer id,
                                                           @Valid @RequestBody StationInventoryUpdateRequest req) {
        return ResponseEntity.ok(stationInventoryService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<StationInventoryResponse> delete(@PathVariable Integer id) {
        stationInventoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/list")
    public ResponseEntity<Page<StationInventoryResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(stationInventoryService.list(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<StationInventoryResponse>> search(
            @RequestParam(required = false) Integer stationId,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        return ResponseEntity.ok(stationInventoryService.search(stationId, vehicleId, q, pageable));
    }
}
