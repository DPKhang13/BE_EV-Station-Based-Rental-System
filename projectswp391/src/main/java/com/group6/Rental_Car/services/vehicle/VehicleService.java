package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;

import java.util.List;


public interface VehicleService {
    VehicleResponse createVehicle(VehicleCreateRequest vehicleCreateRequest);
    VehicleResponse getVehicleById(Long vehicleId);
    VehicleResponse updateVehicle(Long vehicleId, VehicleUpdateRequest vehicleUpdateRequest);
    void deleteVehicle(Long vehicleId);
    List<VehicleResponse> getAllVehicles();
}


