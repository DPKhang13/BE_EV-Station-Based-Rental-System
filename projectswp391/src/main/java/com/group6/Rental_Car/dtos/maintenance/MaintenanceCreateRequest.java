package com.group6.Rental_Car.dtos.maintenance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MaintenanceCreateRequest {
    private Long vehicleId;
    private String description;
    private LocalDate date;
    private BigDecimal cost;
}
