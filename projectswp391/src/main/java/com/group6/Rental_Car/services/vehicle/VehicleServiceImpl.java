package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleAttribute;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final RentalStationRepository rentalStationRepository;
    private final VehicleAttributeService vehicleAttributeService; // <-- thay vì repository
    private final ModelMapper modelMapper;

    @Override
    public VehicleResponse createVehicle(VehicleCreateRequest req) {
        Vehicle vehicle = modelMapper.map(req, Vehicle.class);

        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new RuntimeException("Rental Station not found"));
            vehicle.setRentalStation(station);
        }

        vehicleRepository.save(vehicle);

        // gọi service attribute
        VehicleAttribute attr = vehicleAttributeService.createOrUpdateAttribute(vehicle, req);

        return vehicleAttributeService.convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse updateVehicle(Long vehicleId, VehicleUpdateRequest req) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        modelMapper.map(req, vehicle);

        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new RuntimeException("Rental Station not found"));
            vehicle.setRentalStation(station);
        }

        vehicleRepository.save(vehicle);

        VehicleAttribute attr = vehicleAttributeService.createOrUpdateAttribute(vehicle, req);

        return vehicleAttributeService.convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse getVehicleById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        var attr = vehicleAttributeService.findByVehicle(vehicle);
        return vehicleAttributeService.convertToDto(vehicle, attr);
    }

    @Override
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        vehicleAttributeService.deleteByVehicle(vehicle);
        vehicleRepository.delete(vehicle);
    }

    @Override
    public List<VehicleResponse> getAllVehicles() {
        return vehicleRepository.findAll().stream()
                .map(v -> vehicleAttributeService.convertToDto(v, vehicleAttributeService.findByVehicle(v)))
                .collect(Collectors.toList());
    }
}
