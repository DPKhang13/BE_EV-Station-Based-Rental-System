package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.OrderService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderServiceRepository extends JpaRepository<OrderService, Long> {

    //  Lọc theo loại dịch vụ (TRAFFIC_FEE | CLEANING | MAINTENANCE | REPAIR | OTHER)
    List<OrderService> findByServiceTypeIgnoreCase(String serviceType);

}

