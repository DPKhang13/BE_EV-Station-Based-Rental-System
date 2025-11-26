package com.group6.Rental_Car.dtos.incidentreport;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IncidentReportResponse {
    private Integer incidentId;
    private Long vehicleId;
    private String fullName;
    private String description;
    private LocalDateTime createdAt;
}

