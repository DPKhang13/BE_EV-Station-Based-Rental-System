package com.group6.Rental_Car.services.vehicle;


import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleAttribute;

public interface VehicleAttributeService {
    VehicleAttribute createOrUpdateAttribute(Vehicle vehicle, VehicleCreateRequest req);
    VehicleAttribute createOrUpdateAttribute(Vehicle vehicle, VehicleUpdateRequest req);
    VehicleAttribute findByVehicle(Vehicle vehicle);
    void deleteByVehicle(Vehicle vehicle);
    VehicleResponse convertToDto(Vehicle vehicle, VehicleAttribute attr);
}
