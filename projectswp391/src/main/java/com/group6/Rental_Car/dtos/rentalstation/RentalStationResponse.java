package com.group6.Rental_Car.dtos.rentalstation;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
public class RentalStationResponse {
    private Integer stationid;
    private String name;
    private String city;
    private String district;
    private String ward;
    private String street;

}
