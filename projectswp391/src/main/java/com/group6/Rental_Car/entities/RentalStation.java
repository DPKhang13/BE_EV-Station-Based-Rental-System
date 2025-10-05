package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "RentalStation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer stationId;

    private String stationName;

    private String address;

    // Một trạm có nhiều xe
    @OneToMany(mappedBy = "rentalStation", fetch = FetchType.LAZY)
    private List<Vehicle> vehicles;

    // Một trạm có thể có nhiều nhân viên
    @OneToMany(mappedBy = "rentalStation", fetch = FetchType.LAZY)
    private List<User> users;
}
