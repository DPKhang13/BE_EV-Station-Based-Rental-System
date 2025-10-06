package com.group6.Rental_Car.dtos.vehicleAttribute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VehicleAttributeRequest {

    private Long vehicleId;
    private String plateNumber;

    @NotBlank @Size(max=50)
    private String attrName;

    @Size(max=100)
    private String attrValue;
}
