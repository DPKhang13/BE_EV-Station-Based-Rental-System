package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



import java.util.Optional;


@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Integer> {
    Optional<PricingRule> findBySeatCountAndVariantIgnoreCase(Integer seatCount, String variant);
}