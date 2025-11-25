package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    boolean existsByPlateNumber(String plateNumber);

    //Admin Dashboard
    long countByStatus(String status);

    @Query(value = "SELECT station_id FROM vehicle WHERE vehicle_id = :id", nativeQuery = true)
    Integer findStationId(@Param("id") Long id);

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
    @Query(value = """
    SELECT s.station_id, s.name AS station_name,
           COUNT(v.vehicle_id) AS total,
           SUM(CASE WHEN v.status = 'RENTAL' THEN 1 ELSE 0 END) AS rented
    FROM rentalstation s
    LEFT JOIN vehicle v ON v.station_id = s.station_id
    GROUP BY s.station_id, s.name
    ORDER BY s.station_id
    """, nativeQuery = true)
    List<Object[]> vehicleUsagePerStation();

    // Đếm xe theo station
    @Query(value = """
        SELECT rs.station_id, rs.name, COUNT(v.vehicle_id)
        FROM rentalstation rs
        LEFT JOIN vehicle v ON v.station_id = rs.station_id
        GROUP BY rs.station_id, rs.name
        ORDER BY COUNT(v.vehicle_id) DESC
        """, nativeQuery = true)
    List<Object[]> countByStation();

    // Đếm xe theo station và status
    long countByRentalStation_StationIdAndStatus(Integer stationId, String status);

    // Lấy tất cả xe theo stationId (bao gồm MAINTENANCE), sắp xếp theo biển số
    List<Vehicle> findByRentalStation_StationIdOrderByPlateNumberAsc(Integer stationId);
    
    // Lấy xe theo stationId và status, sắp xếp theo biển số
    List<Vehicle> findByRentalStation_StationIdAndStatusOrderByPlateNumberAsc(Integer stationId, String status);
}
