package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.RentalStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RentalStationRepository extends JpaRepository<RentalStation, Integer> {

    List<RentalStation> findByNameContainingIgnoreCaseOrCityContainingIgnoreCaseOrDistrictContainingIgnoreCaseOrWardContainingIgnoreCaseOrStreetContainingIgnoreCase(
            String q1, String  q2, String q3, String q4, String q5);
// create check
    boolean existsByNameIgnoreCaseAndCityIgnoreCaseAndDistrictIgnoreCaseAndWardIgnoreCaseAndStreetIgnoreCase(
            String name, String city, String district, String ward, String street
    );
    //update check
    boolean existsByNameIgnoreCaseAndCityIgnoreCaseAndDistrictIgnoreCaseAndWardIgnoreCaseAndStreetIgnoreCaseAndStationIdNot(
            String name, String city, String district, String ward, String street, Integer stationId);
}
