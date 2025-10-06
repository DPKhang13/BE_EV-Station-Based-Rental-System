package com.group6.Rental_Car.dtos.vehicleAttribute;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VehicleAttributeDTO {
    private Long id;
    private Long vehicleId ;
    private String plateNumber;
    private String attrName;
    private String attrValue;

}
