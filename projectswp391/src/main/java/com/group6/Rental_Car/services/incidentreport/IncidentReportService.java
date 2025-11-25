package com.group6.Rental_Car.services.incidentreport;

import com.group6.Rental_Car.dtos.incidentreport.IncidentReportCreateRequest;
import com.group6.Rental_Car.dtos.incidentreport.IncidentReportResponse;
import com.group6.Rental_Car.dtos.incidentreport.IncidentReportUpdateRequest;

import java.util.List;

public interface IncidentReportService {
    IncidentReportResponse create(IncidentReportCreateRequest req);
    IncidentReportResponse update(Integer incidentId, IncidentReportUpdateRequest req);
    void delete(Integer incidentId);
    IncidentReportResponse getById(Integer incidentId);
    List<IncidentReportResponse> getAll();
    List<IncidentReportResponse> getByVehicleId(Long vehicleId);
}

