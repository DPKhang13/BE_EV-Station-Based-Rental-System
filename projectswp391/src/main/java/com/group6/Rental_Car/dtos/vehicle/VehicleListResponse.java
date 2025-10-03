package com.group6.Rental_Car.dtos.vehicle;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class VehicleListResponse { //Response tat ca xe
    private List<VehicleResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
