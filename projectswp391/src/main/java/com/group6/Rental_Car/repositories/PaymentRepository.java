package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByRentalOrder_OrderId(UUID orderId);

}