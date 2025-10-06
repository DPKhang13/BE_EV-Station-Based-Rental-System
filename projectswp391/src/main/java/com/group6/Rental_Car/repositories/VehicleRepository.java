package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @EntityGraph(attributePaths = {"station", "station.address"})
    @Query("SELECT v FROM Vehicle v")
    Page<Vehicle> findAllWithStation(Pageable pageable);

    @EntityGraph(attributePaths = {"station", "station.address"})
    @Query("SELECT v FROM Vehicle v WHERE v.status = :status")
    Page<Vehicle> findAllWithStationByStatus(@Param("status") String status, Pageable pageable);

    @EntityGraph(attributePaths = {"station", "station.address"})
    @Query("SELECT v FROM Vehicle v WHERE v.plateNumber LIKE %:search% OR v.station.name LIKE %:search%")
    Page<Vehicle> findBySearch(@Param("search") String search, Pageable pageable);

    @EntityGraph(attributePaths = {"station", "station.address"})
    @Query("SELECT v FROM Vehicle v WHERE v.status = :status AND (v.plateNumber LIKE %:search% OR v.station.name LIKE %:search%)")
    Page<Vehicle> findByStatusAndSearch(@Param("status") String status, @Param("search") String search, Pageable pageable);

    Optional<Vehicle> findByPlateNumber(String plateNumber);
}
