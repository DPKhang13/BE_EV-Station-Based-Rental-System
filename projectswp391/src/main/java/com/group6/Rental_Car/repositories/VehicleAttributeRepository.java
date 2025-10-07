package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleAttributeRepository extends JpaRepository<VehicleAttribute, Long> {
    Optional<VehicleAttribute> findByVehicle(Vehicle vehicle);
}
