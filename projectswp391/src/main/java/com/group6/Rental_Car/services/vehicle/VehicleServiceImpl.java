package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleListRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleListResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.repositories.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Autowired
    public VehicleServiceImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    // 1. Tạo sản phẩm (POST)
    @Override
    public VehicleResponse createVehicle(VehicleRequest vehicleRequest) {
        if (vehicleRequest.getPlateNumber() == null || vehicleRequest.getPlateNumber().isEmpty()) {
            throw new IllegalArgumentException("Plate number is required");
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(vehicleRequest.getPlateNumber());
        vehicle.setStatus(vehicleRequest.getStatus());
        vehicle.setSeatCount(vehicleRequest.getSeatCount());
        vehicle.setVariant(vehicleRequest.getVariant());

        vehicle = vehicleRepository.save(vehicle);
        return toResponse(vehicle);
    }

    // 2. Lấy tất cả sản phẩm (GET)
    @Override
    public VehicleListResponse getAllVehicles(VehicleListRequest vehicleListRequest) {
        Pageable pageable = PageRequest.of(vehicleListRequest.getPage(), vehicleListRequest.getSize());
        Page<Vehicle> vehicles = vehicleRepository.findAll(pageable);

        VehicleListResponse response = new VehicleListResponse();
        response.setItems(
                vehicles.getContent().stream().map(this::toResponse).collect(Collectors.toList())
        );
        response.setPage(vehicles.getNumber());
        response.setSize(vehicles.getSize());
        response.setTotalElements(vehicles.getTotalElements());
        response.setTotalPages(vehicles.getTotalPages());
        response.setFirst(vehicles.isFirst());
        response.setLast(vehicles.isLast());

        return response;
    }

    // 3. Cập nhật sản phẩm (PUT)
    @Override
    public VehicleResponse updateVehicle(Long vehicleId, VehicleRequest vehicleRequest) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (vehicleRequest.getPlateNumber() == null || vehicleRequest.getPlateNumber().isEmpty()) {
            throw new IllegalArgumentException("Plate number is required");
        }

        vehicle.setPlateNumber(vehicleRequest.getPlateNumber());
        vehicle.setStatus(vehicleRequest.getStatus());
        vehicle.setSeatCount(vehicleRequest.getSeatCount());
        vehicle.setVariant(vehicleRequest.getVariant());

        vehicle = vehicleRepository.save(vehicle);
        return toResponse(vehicle);
    }

    // 4. Xóa sản phẩm (DELETE)
    @Override
    public void deleteVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        vehicleRepository.delete(vehicle);
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        VehicleResponse response = new VehicleResponse();
        response.setVehicleId(vehicle.getVehicleId());
        response.setPlateNumber(vehicle.getPlateNumber());
        response.setStatus(vehicle.getStatus());
        response.setSeatCount(vehicle.getSeatCount());
        response.setVariant(vehicle.getVariant());
        return response;
    }
}