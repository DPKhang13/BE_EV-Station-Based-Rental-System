package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.RentalOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RetalOrderRepository extends JpaRepository<RentalOrder, UUID> {

}
