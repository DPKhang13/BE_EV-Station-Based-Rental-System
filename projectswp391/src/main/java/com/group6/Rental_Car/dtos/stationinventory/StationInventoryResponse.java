package com.group6.Rental_Car.dtos.stationinventory;

import lombok.Data;

@Data
public class StationInventoryResponse {

    private Integer inventoryId;

    private Integer stationId;
    private String stationName;

    private Long vehicleId;
    private String plateNumber;

    private Integer quantity;
}
