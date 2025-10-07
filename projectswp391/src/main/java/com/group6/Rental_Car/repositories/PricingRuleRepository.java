package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Integer> {
    Optional<PricingRule> findByVehicle(Vehicle vehicle);
}