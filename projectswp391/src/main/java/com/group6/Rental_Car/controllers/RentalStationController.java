package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.rentalstation.RentalStationCreateRequest;
import com.group6.Rental_Car.dtos.rentalstation.RentalStationResponse;
import com.group6.Rental_Car.dtos.rentalstation.RentalStationUpdateRequest;
import com.group6.Rental_Car.services.rentalstation.RentalStationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rentalstation")
@RequiredArgsConstructor
@Tag(name= "Api RentalStation", description = "Search, chinh sua va update dia chi tram")
public class RentalStationController {
    @Autowired
    private RentalStationService rentalStationService;

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody RentalStationCreateRequest req){
        RentalStationResponse rentalStationResponse = rentalStationService.create(req);
        return ResponseEntity.ok().body(rentalStationResponse);
    }
    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(
            @PathVariable int id,
            @RequestBody RentalStationUpdateRequest req){
        RentalStationResponse rentalStationResponse = rentalStationService.update(id, req);
        return ResponseEntity.ok().body(rentalStationResponse);
    }
    @GetMapping("/getAll")
    public ResponseEntity<?> getAll(){
        List<RentalStationResponse> rentalStationResponse = rentalStationService.getAll();
        return ResponseEntity.ok().body(rentalStationResponse);
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String q){
        List<RentalStationResponse> rentalStationResponse = rentalStationService.search(q);
        return ResponseEntity.ok().body(rentalStationResponse);
    }
}
