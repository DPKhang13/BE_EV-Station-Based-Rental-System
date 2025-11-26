package com.group6.Rental_Car.services.incidentreport;

import com.group6.Rental_Car.dtos.incidentreport.IncidentReportCreateRequest;
import com.group6.Rental_Car.dtos.incidentreport.IncidentReportResponse;
import com.group6.Rental_Car.dtos.incidentreport.IncidentReportUpdateRequest;
import com.group6.Rental_Car.entities.IncidentReport;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.IncidentReportRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import com.group6.Rental_Car.utils.JwtUserDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.group6.Rental_Car.utils.ValidationUtil.*;

@Service
@RequiredArgsConstructor
public class IncidentReportServiceImpl implements IncidentReportService {
    private final IncidentReportRepository incidentReportRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public IncidentReportResponse create(IncidentReportCreateRequest req) {
        // Get current user from JWT
        UUID userId = getCurrentUserId();
        
        // Validate vehicle
        Long vehicleId = requireNonNull(req.getVehicleId(), "vehicleId");
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Validate description
        String description = trim(req.getDescription());
        if (description == null || description.isBlank()) {
            throw new BadRequestException("description is required");
        }

        // Create incident report
        IncidentReport incidentReport = IncidentReport.builder()
                .vehicle(vehicle)
                .reportedBy(user)
                .description(description)
                .build();

        incidentReport = incidentReportRepository.save(incidentReport);
        return toResponse(incidentReport);
    }

    @Override
    @Transactional
    public IncidentReportResponse update(Integer incidentId, IncidentReportUpdateRequest req) {
        IncidentReport incidentReport = incidentReportRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident report not found: " + incidentId));

        if (req.getDescription() != null) {
            String description = trim(req.getDescription());
            if (description != null && !description.isBlank()) {
                incidentReport.setDescription(description);
            } else {
                throw new BadRequestException("description cannot be empty");
            }
        }

        incidentReport = incidentReportRepository.save(incidentReport);
        return toResponse(incidentReport);
    }

    @Override
    @Transactional
    public void delete(Integer incidentId) {
        IncidentReport incidentReport = incidentReportRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident report not found: " + incidentId));
        incidentReportRepository.delete(incidentReport);
    }

    @Override
    public IncidentReportResponse getById(Integer incidentId) {
        IncidentReport incidentReport = incidentReportRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident report not found: " + incidentId));
        return toResponse(incidentReport);
    }

    @Override
    public List<IncidentReportResponse> getAll() {
        return incidentReportRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<IncidentReportResponse> getByVehicleId(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        
        List<IncidentReport> reports = incidentReportRepository.findByVehicle(vehicle);
        return reports.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private IncidentReportResponse toResponse(IncidentReport incidentReport) {
        IncidentReportResponse response = modelMapper.map(incidentReport, IncidentReportResponse.class);
        if (incidentReport.getVehicle() != null) {
            response.setVehicleId(incidentReport.getVehicle().getVehicleId());
        }
        if (incidentReport.getReportedBy() != null) {
            response.setFullName(incidentReport.getReportedBy().getFullName());
        }
        return response;
    }

    private UUID getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserDetails jwt)) {
            throw new BadRequestException("Phiên đăng nhập không hợp lệ");
        }
        return jwt.getUserId();
    }
}

