package com.group6.Rental_Car.dtos.rentalstation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
    @AllArgsConstructor
    @NoArgsConstructor

public class RentalStationCreateRequest {
    private String name;
    private String city;
    private String district;
    private String ward;
    private String street;
}
