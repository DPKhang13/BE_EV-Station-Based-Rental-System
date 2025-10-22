package com.group6.Rental_Car.entities;
import lombok.*;
import jakarta.persistence.*;

@Entity
@Table(
        name = "stationinventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_station_vehicle",
                columnNames =  {"station_id", "vehicle_id"}
        )
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
public class StationInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Integer inventoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private RentalStation station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
