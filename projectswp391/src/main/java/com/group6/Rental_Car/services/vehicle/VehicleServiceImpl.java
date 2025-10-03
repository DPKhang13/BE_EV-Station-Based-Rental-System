package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleListRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleListResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.entities.RentalStation;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VehicleServiceImpl implements VehicleService {

    @Autowired
    private VehicleRepository vehicleRepository;
    private final RentalStationRepository stationRepository;

    public VehicleServiceImpl(VehicleRepository vehicleRepository,
                              RentalStationRepository stationRepository) {
        this.vehicleRepository = vehicleRepository;
        this.stationRepository = stationRepository;
    }

    // Tạo sản phẩm mới (POST)
    @Override
    @Transactional
    public VehicleResponse createVehicle(VehicleRequest vehicleRequest) {
        // 1) Kiểm tra stationId
        if (vehicleRequest.getStationId() == null) {
            throw new IllegalArgumentException("stationId is required");
        }
        RentalStation station = stationRepository.findById(vehicleRequest.getStationId())
                .orElseThrow(() -> new IllegalArgumentException("Station not found: " + vehicleRequest.getStationId()));

        // 2) (khuyến nghị) kiểm tra trùng plateNumber
        Vehicle dup = vehicleRepository.findByPlateNumber(vehicleRequest.getPlateNumber());
        if (dup != null) {
            throw new IllegalStateException("Plate number already exists: " + vehicleRequest.getPlateNumber());
        }

        // 3) Lập vehicle và gán station
        Vehicle v = new Vehicle();
        v.setStation(station);
        v.setPlateNumber(vehicleRequest.getPlateNumber());
        v.setStatus(vehicleRequest.getStatus());
        v.setSeatCount(vehicleRequest.getSeatCount());
        v.setVariant(vehicleRequest.getVariant());

        v = vehicleRepository.save(v);
        return toResponse(v);
    }

    // Lấy danh sách xe (GET)
    @Override
    public VehicleListResponse getAllVehicles(VehicleListRequest vehicleListRequest) {
        int page = (vehicleListRequest.getPage() == null || vehicleListRequest.getPage() < 0)
                ? 0
                : vehicleListRequest.getPage();
        int size = (vehicleListRequest.getSize() == null || vehicleListRequest.getSize() <= 0)
                ? 20  // Giá trị mặc định cho size
                : vehicleListRequest.getSize();

        Pageable pageable = PageRequest.of(page, size);
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

    // Cập nhật sản phẩm (PUT)
    @Override
    @Transactional
    public VehicleResponse updateVehicle(Long vehicleId, VehicleRequest vehicleRequest) {
        Vehicle existingVehicle = vehicleRepository.findByPlateNumber(vehicleRequest.getPlateNumber());
        if (existingVehicle != null && !existingVehicle.getVehicleId().equals(vehicleId)) {
            throw new RuntimeException("Plate number already exists");
        }
        Optional<Vehicle> vehicleOptional = vehicleRepository.findById(vehicleId);
        Vehicle vehicle = vehicleOptional.orElseThrow(() -> new RuntimeException("Vehicle not found"));

        vehicle.setPlateNumber(vehicleRequest.getPlateNumber());
        vehicle.setStatus(vehicleRequest.getStatus());
        vehicle.setSeatCount(vehicleRequest.getSeatCount());
        vehicle.setVariant(vehicleRequest.getVariant());

        vehicle = vehicleRepository.save(vehicle);
        return toResponse(vehicle);
    }

    // Xóa sản phẩm (DELETE)
    @Override
    @Transactional
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