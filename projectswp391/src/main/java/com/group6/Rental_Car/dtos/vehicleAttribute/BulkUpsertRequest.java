package com.group6.Rental_Car.dtos.vehicleAttribute;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkUpsertRequest {
    @NotEmpty
    private List<BulkUpsertItem> items;
}
