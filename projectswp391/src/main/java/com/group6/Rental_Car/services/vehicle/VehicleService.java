package com.group6.Rental_Car.services.vehicle;


import com.group6.Rental_Car.dtos.vehicle.VehicleListRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleListResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import java.util.UUID;

public interface VehicleService {
    VehicleResponse createVehicle(VehicleRequest vehicleRequest);
    VehicleListResponse getAllVehicles(VehicleListRequest vehicleListRequest);
    VehicleResponse updateVehicle(Long vehicleId, VehicleRequest vehicleRequest);
    void deleteVehicle(Long vehicleId);
}
