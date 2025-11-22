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

    @Column(name = "vehicle_name", length = 100)
    private String vehicleName;
    
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl; // Lưu URL ảnh (có thể là 1 URL hoặc JSON array nếu nhiều ảnh)
    
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VehicleModel> attributes;
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RentalOrderDetail> orderDetails;
    @OneToMany(mappedBy = "vehicle", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<VehicleTimeline> timelines;


}
