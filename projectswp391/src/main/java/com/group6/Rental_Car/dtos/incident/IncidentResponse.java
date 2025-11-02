package com.group6.Rental_Car.dtos.incident;

import com.group6.Rental_Car.enums.IncidentSeverity;
import com.group6.Rental_Car.enums.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IncidentResponse {
    private Integer incidentId;
    private Long vehicleId;
    private String plateNumber;
    private Integer stationId;
    private String description;
    private IncidentSeverity severity;
    private IncidentStatus status;

    private LocalDate occurredOn;
    private LocalDateTime reportedAt;
    private LocalDateTime resolvedAt;
    private BigDecimal cost;
    private UUID reportedBy;
}
