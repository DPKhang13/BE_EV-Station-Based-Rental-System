package com.group6.Rental_Car.entities;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name= "VehicleAttribute",
        uniqueConstraints = @UniqueConstraint(name ="UQ_VA", columnNames = {"vehicle_id","attr_name"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VehicleAttribute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attr_id")
    private Long attrId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name= "attr_name", nullable = false, length = 50)
    private String attrName;

    @Column(name ="attr_value", length = 100)
    private String attrValue;
}