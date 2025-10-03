package com.group6.Rental_Car.dtos.vehicle;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleListRequest {
    private int page =0;
    private int size=20;
    private String status;
    private String search;
}
