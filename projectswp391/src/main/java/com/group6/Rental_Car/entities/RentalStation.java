package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "[RentalStation]")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalStation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "district", nullable = false)
    private String district;

    @Column(name = "ward", nullable = false)
    private String ward;

    @Column(name = "street", nullable = false)
    private String street;

}
