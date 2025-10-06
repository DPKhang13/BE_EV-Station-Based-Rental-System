package com.group6.Rental_Car.dtos.vehicleAttribute;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkUpsertResult {
    private int created;
    private int updated;
}
