package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table (name = "maintenance")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Maintenance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column (name = "maintenance_id")
    private Integer maintenanceId;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "cost", precision = 18, scale = 2, nullable = false)
    private BigDecimal cost;
}
