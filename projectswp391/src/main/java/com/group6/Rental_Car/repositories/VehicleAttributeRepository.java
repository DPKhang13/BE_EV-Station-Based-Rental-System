package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.jar.Attributes;

public interface VehicleAttributeRepository  extends JpaRepository<VehicleAttribute, Long> {
    @EntityGraph(attributePaths = {"vehicle"})
    Page<VehicleAttribute> findByVehicle_Id(Long vehicleId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle"})
    Page<VehicleAttribute> findByVehicle_IdAndAttrNameContainingIgnoreCaseOrVehicle_IdAndAttrValueContainingIgnoreCase(
            Long vehicleId, String q1, Long vehicleId2, String q2, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle"})
Page<VehicleAttribute> findByVehicle_PlateNumberContainingIgnoreCase(String plateNumber, Pageable pageable);

    boolean existsByVehicle_IdAndAttrNameIgnoreCase(Long vehicleId, String attrName);


    Optional<VehicleAttribute> findByVehicle_IdAndAttrNameIgnoreCase(Long vehicleId, String attrName);
    Page<VehicleAttribute> findAllBy(Pageable pageable);

}
