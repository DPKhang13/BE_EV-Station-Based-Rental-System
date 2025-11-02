package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    boolean existsByPlateNumber(String plateNumber);

    //Admin Dashboard
    long countByStatus(String status);

    // xe theo trạm
    @Query(value = """
        SELECT rs.station_id   AS stationId,
               rs.name         AS stationName,
               COUNT(v.vehicle_id) AS total
        FROM rentalstation rs
        LEFT JOIN vehicle v ON v.station_id = rs.station_id
        GROUP BY rs.station_id, rs.name
        ORDER BY total DESC
        """, nativeQuery = true)
    List<Object[]> vehiclesPerStation();
}
