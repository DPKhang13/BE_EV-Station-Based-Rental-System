package com.group6.Rental_Car.dtos.incidentreport;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentReportCreateRequest {
    private Long vehicleId;
    private String description;
}

