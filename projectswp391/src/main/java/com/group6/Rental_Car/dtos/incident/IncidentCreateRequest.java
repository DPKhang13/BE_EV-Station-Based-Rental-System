package com.group6.Rental_Car.dtos.incident;

import com.group6.Rental_Car.enums.IncidentSeverity;
import com.group6.Rental_Car.enums.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IncidentCreateRequest {
    private Long vehicleId;
    private Integer stationId;
    private String description;
    private IncidentSeverity severity;
    private IncidentStatus status;
    private LocalDate occurredOn;
    private BigDecimal cost;
    private UUID reportedBy;
}
