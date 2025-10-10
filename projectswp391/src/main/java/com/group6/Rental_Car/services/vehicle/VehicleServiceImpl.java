package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleAttribute;
import com.group6.Rental_Car.exceptions.BadRequestException;
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

import static com.group6.Rental_Car.utils.ValidationUtil.*;

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
                .orElseThrow(() -> new ResourceNotFoundException("Station: " + stationId));

        //Validate seatCount && variant
        Integer seat = req.getSeatCount();
        String normalizedVariant = validateVariantBySeatCount(seat, req.getVariant());


        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rental Station "));
            vehicle.setRentalStation(station);
        }

        vehicleRepository.save(vehicle);

        // gọi service attribute
        VehicleCreateRequest attrReq = new VehicleCreateRequest();
        attrReq.setBrand(req.getBrand());
        attrReq.setColor(req.getColor());
        attrReq.setSeatCount(seat);
        attrReq.setSeatCount(seat);
        attrReq.setVariant(normalizedVariant);// << dùng biến đã validate/normalize
        attrReq.setBatteryStatus(req.getBatteryStatus());
        attrReq.setBatteryCapacity(req.getBatteryCapacity());
        attrReq.setRangeKm(req.getRangeKm());

        VehicleAttribute attr = vehicleAttributeService.createOrUpdateAttribute(vehicle, attrReq);


        return vehicleAttributeService.convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse updateVehicle(Long vehicleId, VehicleUpdateRequest req) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle "));

        // status (nếu client gửi lên)
        if (req.getStatus() != null) {
            String status = req.getStatus().trim().toLowerCase();
            if (!status.equals("available") && !status.equals("rented") && !status.equals("maintenance")) {
                throw new BadRequestException("status must be one of: available|rented|maintenance");
            }
            vehicle.setStatus(status);
        }

        // station (nếu client gửi lên)
        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rental Station "));
            vehicle.setRentalStation(station);
        }


        modelMapper.map(req, vehicle);
        vehicle = vehicleRepository.save(vehicle);

        // Lấy attribute hiện tại để tính giá trị hiệu lực khi client chỉ gửi 1 trong 2
        VehicleAttribute currentAttr = vehicleAttributeService.findByVehicle(vehicle);

        Integer effectiveSeat = (req.getSeatCount() != null)
                ? req.getSeatCount()
                : (currentAttr != null ? currentAttr.getSeatCount() : null);

        String effectiveVariantRaw = (req.getVariant() != null)
                ? req.getVariant()
                : (currentAttr != null ? currentAttr.getVariant() : null);

        String normalizedVariant = null;
        if (effectiveSeat != null || effectiveVariantRaw != null) {
            normalizedVariant = validateVariantBySeatCount(effectiveSeat, effectiveVariantRaw);
        }

        VehicleUpdateRequest attrReq = new VehicleUpdateRequest();
        if (req.getSeatCount() != null) attrReq.setSeatCount(effectiveSeat);
        if (req.getVariant() != null || (effectiveSeat != null && currentAttr == null)) {
            attrReq.setVariant(normalizedVariant);
        }
        attrReq.setBrand(req.getBrand());
        attrReq.setColor(req.getColor());
        attrReq.setBatteryStatus(req.getBatteryStatus());
        attrReq.setBatteryCapacity(req.getBatteryCapacity());
        attrReq.setRangeKm(req.getRangeKm());

        vehicleRepository.save(vehicle);

        VehicleAttribute attr = vehicleAttributeService.createOrUpdateAttribute(vehicle, req);

        return vehicleAttributeService.convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse getVehicleById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle"));

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
