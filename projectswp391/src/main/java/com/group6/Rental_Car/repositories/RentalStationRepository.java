package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.RentalStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RentalStationRepository extends JpaRepository<RentalStation, Integer> {
}
