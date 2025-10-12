package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {}