package com.group6.Rental_Car.services.maintenance;


import com.group6.Rental_Car.dtos.maintenance.MaintenanceCreateRequest;
import com.group6.Rental_Car.dtos.maintenance.MaintenanceResponse;
import com.group6.Rental_Car.dtos.maintenance.MaintenanceUpdateRequest;
import com.group6.Rental_Car.entities.Maintenance;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.MaintenanceRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static com.group6.Rental_Car.utils.ValidationUtil.*;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {
    private final MaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    public MaintenanceResponse create (MaintenanceCreateRequest req){
        Long vehicleId = requireNonNull(req.getVehicleId(), "vehicleId");
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        var date = requireNonNull(req.getDate(), "date");
        BigDecimal cost = requireNonNull(req.getCost(), "cost");
        ensureNonNegative(cost, "cost");

        String description = trim(req.getDescription());
        ensureMaxLength(description, 255, "description");

        Maintenance m = new Maintenance();
        m.setVehicle(vehicle);
        m.setDate(date);
        m.setCost(cost);
        m.setDescription(description);

        m = maintenanceRepository.save(m);
        return toResponse(m);
    }

    @Override
    public MaintenanceResponse update(Integer maintenanceId, MaintenanceUpdateRequest req) {
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance not found: " + maintenanceId));

        if (req.getDate() != null) {
            m.setDate(req.getDate());
        }
        if (req.getCost() != null) {
            ensureNonNegative(req.getCost(), "cost");
            m.setCost(req.getCost());
        }
        if (req.getDescription() != null) {
            String desc = trim(req.getDescription());
            ensureMaxLength(desc, 255, "description");
            m.setDescription(desc);
        }

        m = maintenanceRepository.save(m);
        return toResponse(m);
    }

    @Override
    public void delete(Integer maintenanceId) {
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance not found: " + maintenanceId));
        maintenanceRepository.delete(m);
    }

    @Override
    public MaintenanceResponse getById(Integer maintenanceId) {
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance not found: " + maintenanceId));
        return toResponse(m);

    }

    @Override
    public List<MaintenanceResponse> listAll() {
        return maintenanceRepository.findAll().
                stream().
                map(this::toResponse).
                toList();
    }

    private MaintenanceResponse toResponse(Maintenance m) {
        if (m == null) return null;
        MaintenanceResponse dto = new MaintenanceResponse();
        dto.setMaintenanceId(m.getMaintenanceId());
        dto.setVehicleId(m.getVehicle().getVehicleId()); // Vehicle -> id
        dto.setDescription(m.getDescription());
        dto.setDate(m.getDate());
        dto.setCost(m.getCost());
        return dto;
    }
}
