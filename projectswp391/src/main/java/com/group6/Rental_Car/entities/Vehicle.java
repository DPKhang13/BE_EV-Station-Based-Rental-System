package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "vehicle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vehicleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private RentalStation rentalStation;

    @Column(unique = true, length = 20)
    private String plateNumber;

    private String status;

    private String description;

    // Một xe có nhiều đơn thuê
    @OneToMany(mappedBy = "vehicle", fetch = FetchType.LAZY)
    private List<RentalOrder> rentalOrders;

    // Một xe có thể có một bảng giá
    @OneToOne(mappedBy = "vehicle", fetch = FetchType.LAZY)
    private PricingRule pricingRule;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VehicleAttribute> attributes;


}
