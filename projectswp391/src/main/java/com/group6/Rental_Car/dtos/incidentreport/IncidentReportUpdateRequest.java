package com.group6.Rental_Car.dtos.incidentreport;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentReportUpdateRequest {
    private String description;  // optional
}

