package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "[Vehicle]")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_id")
    private Long vehicleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private RentalStation station;

    @Column(name = "plate_number", length = 20, nullable = false, unique = true)
    private String plateNumber;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "seat_count")
    private Integer seatCount;

    @Column(name = "variant", length = 50)
    private String variant;
}
