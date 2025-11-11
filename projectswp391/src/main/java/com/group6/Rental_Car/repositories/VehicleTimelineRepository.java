package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.VehicleTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VehicleTimelineRepository extends JpaRepository<VehicleTimeline, Long> {
    List<VehicleTimeline> findByVehicle_VehicleId(Long vehicleId);
    List<VehicleTimeline> findByVehicle_VehicleIdAndDayBetween(Long vehicleId, LocalDate from, LocalDate to);
}
