package com.group6.Rental_Car.services.incident;

import com.group6.Rental_Car.dtos.incident.IncidentCreateRequest;
import com.group6.Rental_Car.dtos.incident.IncidentResponse;
import com.group6.Rental_Car.dtos.incident.IncidentUpdateRequest;

import java.util.List;

public interface IncidentService {
    IncidentResponse create(IncidentCreateRequest req);
    IncidentResponse update(Integer incidentId, IncidentUpdateRequest req);
    void delete(Integer incidentId);
    IncidentResponse getById(Integer incidentId);
    List<IncidentResponse> listAll();
}
