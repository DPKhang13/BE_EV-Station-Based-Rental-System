package com.group6.Rental_Car.dtos.vehicleAttribute;

import lombok.*;
import jakarta.validation.constraints.NotBlank;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class    BulkUpsertItem {
    private Long vehicleId;
    private String plateNumber;

    @NotBlank
    private String attrName;
    private String attrValue;
}
