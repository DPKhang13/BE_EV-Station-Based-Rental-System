package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicleattribute")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attr_id")
    private Long attrId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "brand", length = 50)
    private String brand;

    private String color;

    private String transmission;

    private Integer seatCount;

    @Column(name = "year")
    private Integer year;

    private String variant;

    @Column(name = "battery_status", length = 50)
    private String batteryStatus;

    @Column(name = "battery_capacity", length = 50)
    private String batteryCapacity;

    @Column(name = "range_km")
    private Integer rangeKm;
}
