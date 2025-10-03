package com.group6.Rental_Car.dtos.vehicle;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleListRequest {
    private Integer page =0;
    private Integer size=20;
    private String status;
    private String search;
}
