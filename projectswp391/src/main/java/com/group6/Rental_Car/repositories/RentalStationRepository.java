package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.RentalStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RentalStationRepository extends JpaRepository<RentalStation, Long> {
    Optional<RentalStation> findById(Long stationId);

    Optional<RentalStation> findByName(String name);

    // Tìm RentalStation theo thành phố (có thể dùng cho lọc)
    Optional<RentalStation> findByCity(String city);
}
