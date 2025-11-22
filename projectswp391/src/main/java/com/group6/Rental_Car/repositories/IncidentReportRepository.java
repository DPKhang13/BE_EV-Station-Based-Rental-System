package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.IncidentReport;
import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Integer> {
    List<IncidentReport> findByVehicle(Vehicle vehicle);
    List<IncidentReport> findByVehicle_VehicleId(Long vehicleId);
}

