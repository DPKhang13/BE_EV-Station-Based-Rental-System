package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {
    Optional<VehicleModel> findByVehicle(Vehicle vehicle);
}
