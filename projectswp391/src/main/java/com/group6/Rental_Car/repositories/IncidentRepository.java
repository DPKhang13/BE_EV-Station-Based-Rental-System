package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Incident;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.enums.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Integer> {

    List<Incident> findByVehicle_VehicleId(Long vehicleId);

    long countByStatus(IncidentStatus status);

    @Query("SELECT i FROM Incident i WHERE i.occurredOn BETWEEN :from AND :to")
    List<Incident> findAllInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Tổng chi phí theo LocalDate (cast cột timestamp -> date để bind LocalDate an toàn)
    @Query(value = """
        SELECT COALESCE(SUM(cost),0)
        FROM incident
        WHERE occurred_at::date BETWEEN :from AND :to
        """, nativeQuery = true)
    Double totalCostBetween(@Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    // Tổng chi phí theo Timestamp (dùng khi bạn đã có tsFrom/tsTo)
    @Query(value = """
        SELECT COALESCE(SUM(cost),0)
        FROM incident
        WHERE occurred_at BETWEEN :from AND :to
        """, nativeQuery = true)
    Double totalCostBetweenTs(@Param("from") Timestamp from,
                              @Param("to") Timestamp to);

    // Đếm incident theo ngày để vẽ chart
    @Query(value = """
        SELECT DATE_TRUNC('day', occurred_at)::date AS d, COUNT(*) AS c
        FROM incident
        WHERE occurred_at BETWEEN :from AND :to
        GROUP BY d
        ORDER BY d
        """, nativeQuery = true)
    List<Object[]> incidentsByDay(@Param("from") Timestamp from,
                                  @Param("to") Timestamp to);

    // 10 incident gần nhất trong khoảng ngày
    @Query(value = """
        SELECT i.incident_id, i.vehicle_id, v.vehicle_name, i.title, i.description,
               i.severity, i.status, i.occurred_at, i.cost
        FROM incident i
        JOIN vehicle v ON v.vehicle_id = i.vehicle_id
        WHERE i.occurred_at BETWEEN :from AND :to
        ORDER BY i.occurred_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> recentIncidents(@Param("from") Timestamp from,
                                   @Param("to") Timestamp to,
                                   @Param("limit") int limit);
}
