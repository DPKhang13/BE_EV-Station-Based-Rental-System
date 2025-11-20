package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {
    Optional<VehicleModel> findByVehicle(Vehicle vehicle);
    List<VehicleModel> findByCarmodelIgnoreCase(String carmodel);
    List<VehicleModel> findByColorIgnoreCaseAndCarmodelIgnoreCaseAndVariantIgnoreCase(
            String color, String carmodel, String variant);
}
