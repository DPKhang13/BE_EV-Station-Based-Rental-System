package com.group6.Rental_Car.dtos.vehicle;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class VehicleResponse { //Response cho 1 san pham
    private Long vehicleId;
    private String plateNumber;
    private String status;
    private int seatCount;
    private String variant;
    private String stationName;
    private String fullAddress;
    }
