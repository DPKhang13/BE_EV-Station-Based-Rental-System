package com.group6.Rental_Car.services.incident;

import com.group6.Rental_Car.dtos.incident.IncidentCreateRequest;
import com.group6.Rental_Car.dtos.incident.IncidentResponse;
import com.group6.Rental_Car.dtos.incident.IncidentUpdateRequest;
import com.group6.Rental_Car.entities.Incident;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.enums.IncidentStatus;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.IncidentRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.group6.Rental_Car.utils.ValidationUtil.*;

@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidentRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    public IncidentResponse create(IncidentCreateRequest req) {
        Long vehicleId = requireNonNull(req.getVehicleId(), "vehicleId");
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        String description = trim(req.getDescription());
        ensureMaxLength(description, 1000, "description");

        BigDecimal cost = req.getCost();
        if (cost != null) ensureNonNegative(cost, "cost");

        LocalDate occurredOn = (req.getOccurredOn() != null) ? req.getOccurredOn() : LocalDate.now();

        Incident incident = new Incident();
        incident.setVehicle(vehicle);
        incident.setDescription(description);

        // Lấy station_id từ DB
        Integer stationId = vehicleRepository.findStationId(vehicleId);
        incident.setStationId(stationId);

        incident.setSeverity(req.getSeverity());
        incident.setStatus(req.getStatus() != null ? req.getStatus() : IncidentStatus.OPEN);
        incident.setOccurredOn(occurredOn);
        incident.setCost(cost);
        incident.setReportedBy(req.getReportedBy());
        incident.setReportedAt(LocalDateTime.now());

        // Nếu tạo mới đã ở trạng thái RESOLVED -> đóng dấu thời gian
        if (incident.getStatus() == IncidentStatus.RESOLVED && incident.getResolvedAt() == null) {
            incident.setResolvedAt(LocalDateTime.now());
        }

        incident = incidentRepository.save(incident);
        return toResponse(incident);
    }

    @Override
    public IncidentResponse update(Integer incidentId, IncidentUpdateRequest req) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        IncidentStatus before = incident.getStatus();

        if (req.getVehicleId() != null) {
            Long newVehicleId = req.getVehicleId();
            Vehicle vehicle = vehicleRepository.findById(newVehicleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + newVehicleId));
            incident.setVehicle(vehicle);

            // cập nhật lại station_id theo vehicle mới
            Integer stationId = vehicleRepository.findStationId(newVehicleId);
            incident.setStationId(stationId);
        }

        if (req.getDescription() != null) {
            String description = trim(req.getDescription());
            ensureMaxLength(description, 1000, "description");
            incident.setDescription(description);
        }
        if (req.getSeverity() != null) {
            incident.setSeverity(req.getSeverity());
        }
        if (req.getStatus() != null) {
            incident.setStatus(req.getStatus());
        }
        if (req.getOccurredOn() != null) {
            incident.setOccurredOn(req.getOccurredOn());
        }
        if (req.getCost() != null) {
            ensureNonNegative(req.getCost(), "cost");
            incident.setCost(req.getCost());
        }
        if (req.getReportedBy() != null) {
            incident.setReportedBy(req.getReportedBy());
            if (incident.getReportedAt() == null) {
                incident.setReportedAt(LocalDateTime.now());
            }
        }

        // Tự động xử lý resolvedAt khi đổi trạng thái
        IncidentStatus after = incident.getStatus();
        boolean movedToResolved = (before != IncidentStatus.RESOLVED && after == IncidentStatus.RESOLVED);
        if (movedToResolved && incident.getResolvedAt() == null) {
            incident.setResolvedAt(LocalDateTime.now());
        }
        boolean reopened = (before == IncidentStatus.RESOLVED && after == IncidentStatus.OPEN);
        if (reopened) {
            incident.setResolvedAt(null);
        }

        incident = incidentRepository.save(incident);
        return toResponse(incident);
    }

    @Override
    public void delete(Integer incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
        incidentRepository.delete(incident);
    }

    @Override
    public IncidentResponse getById(Integer incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
        return toResponse(incident);
    }

    @Override
    public List<IncidentResponse> listAll() {
        return incidentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private IncidentResponse toResponse(Incident i) {
        if (i == null) return null;

        IncidentResponse dto = new IncidentResponse();
        dto.setIncidentId(i.getIncidentId());
        dto.setVehicleId(i.getVehicle() != null ? i.getVehicle().getVehicleId() : null);
        dto.setPlateNumber(i.getVehicle() != null ? i.getVehicle().getPlateNumber() : null);
        dto.setStationId(i.getStationId());
        dto.setDescription(i.getDescription());
        dto.setSeverity(i.getSeverity());
        dto.setStatus(i.getStatus());
        dto.setOccurredOn(i.getOccurredOn());
        dto.setReportedAt(i.getReportedAt());
        dto.setResolvedAt(i.getResolvedAt());
        dto.setCost(i.getCost());
        dto.setReportedBy(i.getReportedBy());
        return dto;
    }
}
