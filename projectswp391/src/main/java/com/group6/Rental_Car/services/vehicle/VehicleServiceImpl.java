package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateStatusRequest;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleModel;
import com.group6.Rental_Car.entities.VehicleTimeline;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ConflictException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.VehicleModelRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import com.group6.Rental_Car.repositories.VehicleTimelineRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.group6.Rental_Car.utils.ValidationUtil.*;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {
    private static final Set<String> ALLOWED_STATUS = Set.of("available", "rented", "maintenance","BOOKED");
    private static final Set<String> ALLOWED_VARIANT = Set.of("air", "pro", "plus");

    private final VehicleRepository vehicleRepository;
    private final RentalStationRepository rentalStationRepository;
    private final VehicleTimelineRepository vehicleTimelineRepository;
    private final VehicleModelService vehicleModelService; // <-- thay vì repository
    private final ModelMapper modelMapper;
    private final VehicleModelRepository vehicleModelRepository;

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

        Integer seat = req.getSeatCount();
        String normalizedVariant = validateVariantBySeatCount(seat, req.getVariant());


        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rental Station not found "));
            vehicle.setRentalStation(station);
        }

        vehicleRepository.save(vehicle);

        VehicleCreateRequest attrReq = new VehicleCreateRequest();
        attrReq.setBrand(req.getBrand());
        attrReq.setColor(req.getColor());
        attrReq.setSeatCount(seat);
        attrReq.setVariant(normalizedVariant);// << dùng biến đã validate/normalize
        attrReq.setBatteryStatus(req.getBatteryStatus());
        attrReq.setBatteryCapacity(req.getBatteryCapacity());
        attrReq.setRangeKm(req.getRangeKm());

        VehicleModel attr = vehicleModelService.createModel(vehicle, attrReq);


        return vehicleModelService.convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse updateVehicle(Long vehicleId, VehicleUpdateRequest req) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found "));

        // status (nếu client gửi lên)
        if (req.getStatus() != null) {
            String status = req.getStatus().trim().toLowerCase();
            if (!status.equalsIgnoreCase("available") && !status.equalsIgnoreCase("rented") && !status.equalsIgnoreCase("maintenance")) {
                throw new BadRequestException("status must be one of: available|rented|maintenance");
            }
            vehicle.setStatus(status);
        }

        // station (nếu client gửi lên)
        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rental Station not found "));
            vehicle.setRentalStation(station);
        }


        modelMapper.map(req, vehicle);
        vehicle = vehicleRepository.save(vehicle);

        // Lấy attribute hiện tại để tính giá trị hiệu lực khi client chỉ gửi 1 trong 2
        VehicleModel currentAttr = vehicleModelService.findByVehicle(vehicle);

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

        VehicleModel attr = vehicleModelService.updateModel(vehicle, req);

        return vehicleModelService.convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse getVehicleById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        var attr = vehicleModelService.findByVehicle(vehicle);
        return vehicleModelService.convertToDto(vehicle, attr);
    }

    @Override
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        vehicleModelService.deleteByVehicle(vehicle);
        vehicleRepository.delete(vehicle);
    }

    @Override
    public List<VehicleResponse> getAllVehicles() {
        return vehicleRepository.findAll().stream()
                .map(v -> vehicleModelService.convertToDto(v, vehicleModelService.findByVehicle(v)))
                .collect(Collectors.toList());
    }

    @Override
    public VehicleResponse updateStatusVehicle(Long vehicleId, VehicleUpdateStatusRequest req) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        String oldStatus = vehicle.getStatus();
        String newStatus = null;

        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            newStatus = req.getStatus().trim().toUpperCase();
            vehicle.setStatus(newStatus);
        }

        vehicleRepository.save(vehicle);

        // Xử lý timeline dựa trên status mới
        if (newStatus != null) {
            handleTimelineOnStatusChange(vehicle, oldStatus, newStatus);
        }

        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        if (model == null) {
            throw new ResourceNotFoundException("VehicleModel not found for vehicleId: " + vehicleId);
        }

        if (req.getBatteryStatus() != null && !req.getBatteryStatus().isBlank()) {
            model.setBatteryStatus(req.getBatteryStatus().trim());
            vehicleModelRepository.save(model);
        }

        return vehicleModelService.convertToDto(vehicle, model);
    }

    /**
     * Xử lý timeline khi staff thay đổi status của xe
     */
    private void handleTimelineOnStatusChange(Vehicle vehicle, String oldStatus, String newStatus) {
        // Nếu chuyển về AVAILABLE → xóa tất cả timeline
        if ("AVAILABLE".equalsIgnoreCase(newStatus)) {
            deleteAllTimelinesForVehicle(vehicle.getVehicleId());
        }
        // Nếu chuyển sang MAINTENANCE → tạo timeline MAINTENANCE
        else if ("MAINTENANCE".equalsIgnoreCase(newStatus)) {
            createMaintenanceTimeline(vehicle, "Xe đang bảo trì", null);
        }
        // Nếu chuyển sang CHECKING → tạo timeline CHECKING
        else if ("CHECKING".equalsIgnoreCase(newStatus)) {
            createCheckingTimeline(vehicle, "Xe đang được kiểm tra");
        }
    }

    /**
     * Xóa tất cả timeline của xe
     */
    private void deleteAllTimelinesForVehicle(Long vehicleId) {
        if (vehicleId == null) return;

        List<VehicleTimeline> timelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId);
        if (!timelines.isEmpty()) {
            vehicleTimelineRepository.deleteAll(timelines);
        }
    }

    /**
     * Tạo timeline MAINTENANCE
     */
    private void createMaintenanceTimeline(Vehicle vehicle, String note, LocalDateTime endTime) {
        // Xóa timeline cũ trước
        deleteAllTimelinesForVehicle(vehicle.getVehicleId());

        LocalDateTime now = LocalDateTime.now();
        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(vehicle)
                .day(now.toLocalDate())
                .startTime(now)
                .endTime(endTime != null ? endTime : now.plusDays(7)) // Mặc định 7 ngày
                .status("MAINTENANCE")
                .sourceType("VEHICLE_MAINTENANCE")
                .note(note != null ? note : "Xe đang bảo trì")
                .updatedAt(now)
                .build();
        vehicleTimelineRepository.save(timeline);
    }

    private void createCheckingTimeline(Vehicle vehicle, String note) {
        // Xóa timeline cũ trước
        deleteAllTimelinesForVehicle(vehicle.getVehicleId());

        LocalDateTime now = LocalDateTime.now();
        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(vehicle)
                .day(now.toLocalDate())
                .startTime(now)
                .endTime(now.plusDays(1)) // Mặc định 1 ngày
                .status("CHECKING")
                .sourceType("VEHICLE_CHECKING")
                .note(note != null ? note : "Xe đang được kiểm tra")
                .build();
        vehicleTimelineRepository.save(timeline);
    }
}
