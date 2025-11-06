package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Incident;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.enums.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Integer> {

    List<Incident> findByVehicle_VehicleId(Long vehicleId);

    long countByStatus(IncidentStatus status);

    // JPQL: lọc theo khoảng ngày xảy ra (occurred_on là DATE)
    @Query("SELECT i FROM Incident i WHERE i.occurredOn BETWEEN :from AND :to")
    List<Incident> findAllInRange(@Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    // Tổng chi phí theo LocalDate (đúng với occurred_on: DATE)
    @Query(value = """
        SELECT COALESCE(SUM(cost),0)
        FROM incident
        WHERE occurred_on BETWEEN :from AND :to
        """, nativeQuery = true)
    Double totalCostBetween(@Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    // Nếu vẫn muốn variant dùng Timestamp, ép về date cho an toàn
    @Query(value = """
        SELECT COALESCE(SUM(cost),0)
        FROM incident
        WHERE occurred_on BETWEEN CAST(:from AS date) AND CAST(:to AS date)
        """, nativeQuery = true)
    Double totalCostBetweenTs(@Param("from") Timestamp from,
                              @Param("to") Timestamp to);

    // Đếm incident theo ngày (group thẳng theo occurred_on là DATE)
    @Query(value = """
        SELECT occurred_on AS d, COUNT(*) AS c
        FROM incident
        WHERE occurred_on BETWEEN CAST(:from AS date) AND CAST(:to AS date)
        GROUP BY occurred_on
        ORDER BY occurred_on
        """, nativeQuery = true)
    List<Object[]> incidentsByDay(@Param("from") Timestamp from,
                                  @Param("to") Timestamp to);

    // Recent incidents theo reported_at (timestamp) — phục vụ dashboard
    @Query(value = """
    SELECT i.incident_id, i.vehicle_id, v.vehicle_name,
           i.description, i.severity, i.status, i.occurred_on, i.cost
    FROM incident i
    JOIN vehicle v ON v.vehicle_id = i.vehicle_id
    WHERE i.reported_at BETWEEN :from AND :to
    ORDER BY i.reported_at DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> recentIncidents(@Param("from") Timestamp from,
                                   @Param("to") Timestamp to,
                                   @Param("limit") int limit);

    // Tuỳ chọn: list sort sẵn theo reported_at
    List<Incident> findAllByOrderByReportedAtDesc();
}
