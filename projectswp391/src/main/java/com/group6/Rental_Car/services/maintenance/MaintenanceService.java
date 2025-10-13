package com.group6.Rental_Car.services.maintenance;

import com.group6.Rental_Car.dtos.maintenance.MaintenanceCreateRequest;
import com.group6.Rental_Car.dtos.maintenance.MaintenanceResponse;
import com.group6.Rental_Car.dtos.maintenance.MaintenanceUpdateRequest;
import com.group6.Rental_Car.entities.Maintenance;

import java.util.List;

public interface MaintenanceService {
    MaintenanceResponse create(MaintenanceCreateRequest req);
    MaintenanceResponse update(Integer maintenanceId, MaintenanceUpdateRequest req);
    void delete(Integer maintenanceId);
    MaintenanceResponse getById(Integer maintenanceId);
    List<MaintenanceResponse> listAll();

}
