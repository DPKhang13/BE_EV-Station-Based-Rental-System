package com.group6.Rental_Car.dtos.stationinventory;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class StationInventoryUpdateRequest {
    private Integer stationId;
    private Long vehicleId;

    @Min(0)
    private Integer quantity;
}
