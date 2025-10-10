package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleAttribute;
import com.group6.Rental_Car.exceptions.ConflictException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.group6.Rental_Car.utils.VehicleValidationUtil.*;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {
    private static final Set<String> ALLOWED_STATUS = Set.of("available", "rented", "maintenance");
    private static final Set<String> ALLOWED_VARIANT = Set.of("air", "pro", "plus"); //Null cũng hợp lệ

    private final VehicleRepository vehicleRepository;
    private final RentalStationRepository rentalStationRepository;
    private final VehicleAttributeService vehicleAttributeService; // <-- thay vì repository
    private final ModelMapper modelMapper;

    @Override
    public VehicleResponse createVehicle(VehicleCreateRequest req) {
        Vehicle vehicle = modelMapper.map(req, Vehicle.class);

        //Validate thuộc tính của Vehicle
        String plate = requireNonBlank(trim(req.getPlateNumber()), "plateNumber");
        ensureMaxLength(plate, 20, "plateNumber");
        if (vehicleRepository.existsByPlateNumber(plate))
            throw new ConflictException("plateNumber already exists");

        String status = requireNonBlank(trim(req.getStatus()), "status");
        ensureInSetIgnoreCase(status, ALLOWED_STATUS, "status");

        Integer stationId = requireNonNull(req.getStationId(), "stationId");
        var st = rentalStationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + stationId));

        String variant = normalizeNullableLower(req.getVariant());
        if (variant != null) ensureInSetIgnoreCase(variant, ALLOWED_VARIANT, "variant");

        Integer seat = req.getSeatCount();
        if (seat == null || (seat != 4 && seat != 7)) {
            throw new RuntimeException("seatCount must be 4 or 7 (required)");
        }

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

        // status (nếu client gửi lên)
        if (req.getStatus() != null) {
            String status = req.getStatus().trim().toLowerCase();
            if (!status.equals("available") && !status.equals("rented") && !status.equals("maintenance")) {
                throw new RuntimeException("status must be one of: available|rented|maintenance");
            }
            vehicle.setStatus(status);
        }

        // station (nếu client gửi lên)
        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new RuntimeException("Rental Station not found"));
            vehicle.setRentalStation(station);
        }


        modelMapper.map(req, vehicle);

        // VALIDATE attribute rồi update
        if (req.getVariant() != null) {
            String variant = req.getVariant().trim().toLowerCase();
            if (!variant.equals("air") && !variant.equals("pro") && !variant.equals("plus")) {
                throw new RuntimeException("variant must be one of: air|pro|plus");
            }
        }
        if (req.getSeatCount() != null && (req.getSeatCount() < 1 || req.getSeatCount() > 50)) {
            throw new RuntimeException("seatCount must be in [1,50]");
        }
        if (req.getColor() != null && req.getColor().trim().length() > 50) {
            throw new RuntimeException("color length must be <= 50");
        }

        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new RuntimeException("Rental Station not found"));
            vehicle.setRentalStation(station);
        }

        if (req.getSeatCount() != null) {
            int seat = req.getSeatCount();
            if (seat != 4 && seat != 7) {
                throw new RuntimeException("seatCount must be 4 or 7");
            }
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
