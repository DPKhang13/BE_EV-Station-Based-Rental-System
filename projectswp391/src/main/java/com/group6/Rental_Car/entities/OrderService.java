package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "orderservice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "service_type", nullable = false, length = 100)
    private String serviceType; // TRAFFIC_FEE | CLEANING | MAINTENANCE | REPAIR | OTHER

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost = BigDecimal.ZERO;
}
