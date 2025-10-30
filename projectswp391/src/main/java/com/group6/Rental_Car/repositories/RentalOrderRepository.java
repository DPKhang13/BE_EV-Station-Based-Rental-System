package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.RentalOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RentalOrderRepository extends JpaRepository<RentalOrder, UUID> {
    List<RentalOrder> findByCustomer_UserId(UUID customerId);
    @EntityGraph(attributePaths = {"customer", "vehicle"})
    List<RentalOrder> findByStatus(String status);
}
