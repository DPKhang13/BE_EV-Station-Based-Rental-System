package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Maintenance;
import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceRepository extends JpaRepository<Maintenance,Integer> {
    List<Maintenance> findByVehicle(Vehicle vehicle);
}
