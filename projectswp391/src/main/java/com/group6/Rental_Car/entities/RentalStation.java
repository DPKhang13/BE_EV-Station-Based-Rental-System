package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "rentalstation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "name", nullable = false)
    private String name;

    private String city;
    private String district;
    private String ward;
    private String street;

    @OneToMany(mappedBy = "rentalStation", fetch = FetchType.LAZY)
    private List<Vehicle> vehicles;

    @OneToMany(mappedBy = "rentalStation", fetch = FetchType.LAZY)
    private List<User> users;
}
