package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Maintenance;
import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MaintenanceRepository extends JpaRepository<Maintenance,Integer> {
    List<Maintenance> findByVehicle(Vehicle vehicle);

    //Admin Dashboard
    @Query(value = """
        SELECT COALESCE(SUM(cost),0)
        FROM maintenance
        WHERE date BETWEEN :from AND :to
        """, nativeQuery = true)
    Double totalCostBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
