package com.group6.Rental_Car.services.rentalstation;


import com.group6.Rental_Car.dtos.rentalstation.RentalStationCreateRequest;
import com.group6.Rental_Car.dtos.rentalstation.RentalStationResponse;
import com.group6.Rental_Car.dtos.rentalstation.RentalStationUpdateRequest;

import java.util.List;

public interface RentalStationService {
    RentalStationResponse create(RentalStationCreateRequest req);
    RentalStationResponse update(Integer id, RentalStationUpdateRequest req);
    List<RentalStationResponse> getAll();
    List<RentalStationResponse> search(String q);
}
