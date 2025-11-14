package com.group6.Rental_Car.dtos.order;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeVehicleRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long newVehicleId;

    private String note; // Ghi chú lý do đổi xe (optional)
}

