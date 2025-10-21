package com.group6.Rental_Car.services.vehicle;


import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleModel;

public interface VehicleModelService {
    VehicleModel createModel(Vehicle vehicle, VehicleCreateRequest req);
    VehicleModel updateModel(Vehicle vehicle, VehicleUpdateRequest req);
    VehicleModel findByVehicle(Vehicle vehicle);
    void deleteByVehicle(Vehicle vehicle);
    VehicleResponse convertToDto(Vehicle vehicle, VehicleModel attr);
}
