package com.group6.Rental_Car.dtos.stationinventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StationInventoryRequest {

    @NotNull
    private Integer stationId;

    @NotNull
    private Long vehicleId;

    @NotNull @Min(0)
    private Integer quantity;
}
