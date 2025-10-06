package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class VehicleServiceImpl implements VehicleService {
    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private RentalStationRepository rentalStationRepository;

    @Override
    public VehicleResponse createVehicle(VehicleCreateRequest vehicleCreateRequest) {
        Vehicle vehicle = modelMapper.map(vehicleCreateRequest, Vehicle.class);

        // Nếu có stationId => gán rentalStation cho vehicle
        if (vehicleCreateRequest.getStationId() != null) {
            var station = rentalStationRepository.findById(vehicleCreateRequest.getStationId())
                    .orElseThrow(() -> new RuntimeException("Rental Station not found"));
            vehicle.setRentalStation(station);
        }

        vehicleRepository.save(vehicle);
        return convertToDto(vehicle);
    }

    @Override
    public VehicleResponse getVehicleById(Long vehicleId) {
       Vehicle vehicle = vehicleRepository.findById(vehicleId) .orElseThrow(() -> new RuntimeException("Vehicle not found"));
       return modelMapper.map(vehicle, VehicleResponse.class);
    }

    @Override
    public VehicleResponse updateVehicle(Long vehicleId, VehicleUpdateRequest vehicleUpdateRequest) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        // Map các trường cơ bản
        modelMapper.map(vehicleUpdateRequest, vehicle);

        // Nếu có stationId mới trong request => cập nhật lại rentalStation
        if (vehicleUpdateRequest.getStationId() != null) {
            var station = rentalStationRepository.findById(vehicleUpdateRequest.getStationId())
                    .orElseThrow(() -> new RuntimeException("Rental Station not found"));
            vehicle.setRentalStation(station);
        }

        vehicleRepository.save(vehicle);
        return convertToDto(vehicle);
    }


    @Override
    public void deleteVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElseThrow(() -> new RuntimeException("Vehicle not found"));
        vehicleRepository.delete(vehicle);

    }

    @Override
    public List<VehicleResponse> getAllVehicles() {
        return vehicleRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    private VehicleResponse convertToDto(Vehicle vehicle) {
        VehicleResponse dto = modelMapper.map(vehicle, VehicleResponse.class);
        if (vehicle.getRentalStation() != null) {
            dto.setStationId(vehicle.getRentalStation().getStationId());
        }
        return dto;
    }
}
