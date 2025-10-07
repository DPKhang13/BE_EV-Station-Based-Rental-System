package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleAttribute;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.VehicleAttributeRepository;
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
    private final VehicleAttributeRepository vehicleAttributeRepository;
    private final RentalStationRepository rentalStationRepository;
    private final ModelMapper modelMapper;

    @Override
    public VehicleResponse createVehicle(VehicleCreateRequest vehicleCreateRequest) {
        // Map từ DTO sang entity
        Vehicle vehicle = modelMapper.map(vehicleCreateRequest, Vehicle.class);

        // Nếu có stationId => gán rentalStation cho vehicle
        if (vehicleCreateRequest.getStationId() != null) {
            var station = rentalStationRepository.findById(vehicleCreateRequest.getStationId())
                    .orElseThrow(() -> new RuntimeException("Rental Station not found"));
            vehicle.setRentalStation(station);
        }

        // Lưu vehicle trước để có vehicle_id
        vehicleRepository.save(vehicle);

        // Tạo VehicleAttribute mặc định
        VehicleAttribute attr = new VehicleAttribute();
        attr.setVehicle(vehicle);
        attr.setBrand(vehicleCreateRequest.getBrand());
        attr.setColor(vehicleCreateRequest.getColor());
        attr.setTransmission("Automatic"); // mặc định hộp số tự động
        attr.setSeatCount(vehicleCreateRequest.getSeatCount());
        attr.setYear(2025); // mặc định năm 2025
        attr.setVariant(vehicleCreateRequest.getVariant());
        attr.setBatteryStatus(vehicleCreateRequest.getBatteryStatus());
        attr.setBatteryCapacity(vehicleCreateRequest.getBatteryCapacity());
        attr.setRangeKm(0);

        vehicleAttributeRepository.save(attr);

        return convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse getVehicleById(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        var attr = vehicleAttributeRepository.findByVehicle(vehicle).orElse(null);
        return convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse updateVehicle(Long vehicleId, VehicleUpdateRequest req) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        // Cập nhật thông tin vehicle
        modelMapper.map(req, vehicle);

        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new RuntimeException("Rental Station not found"));
            vehicle.setRentalStation(station);
        }

        vehicleRepository.save(vehicle);

        // Lấy hoặc tạo mới attribute
        VehicleAttribute attr = vehicleAttributeRepository.findByVehicle(vehicle)
                .orElseGet(() -> {
                    VehicleAttribute newAttr = new VehicleAttribute();
                    newAttr.setVehicle(vehicle);
                    return newAttr;
                });

        // Cập nhật chỉ field có dữ liệu
        BeanUtils.copyProperties(req, attr, getNullPropertyNames(req));

        vehicleAttributeRepository.save(attr);

        return convertToDto(vehicle, attr);
    }
    @Override
    public void deleteVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        vehicleAttributeRepository.findByVehicle(vehicle)
                .ifPresent(vehicleAttributeRepository::delete);

        vehicleRepository.delete(vehicle);
    }

    @Override
    public List<VehicleResponse> getAllVehicles() {
        return vehicleRepository.findAll().stream()
                .map(vehicle -> {
                    var attr = vehicleAttributeRepository.findByVehicle(vehicle).orElse(null);
                    return convertToDto(vehicle, attr);
                })
                .collect(Collectors.toList());
    }

    private VehicleResponse convertToDto(Vehicle vehicle, VehicleAttribute attr) {
        VehicleResponse dto = modelMapper.map(vehicle, VehicleResponse.class);
        if (vehicle.getRentalStation() != null) {
            dto.setStationId(vehicle.getRentalStation().getStationId());
        }
        // Nếu có attribute => map thêm
        if (attr != null) {
            dto.setBrand(attr.getBrand());
            dto.setColor(attr.getColor());
            dto.setTransmission(attr.getTransmission());
            dto.setSeatCount(attr.getSeatCount());
            dto.setYear(attr.getYear());
            dto.setVariant(attr.getVariant());
            dto.setBatteryStatus(attr.getBatteryStatus());
            dto.setBatteryCapacity(attr.getBatteryCapacity());
            dto.setRangeKm(attr.getRangeKm());
        }
        return dto;
    }

    private String[] getNullPropertyNames(Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        return java.util.Arrays.stream(src.getPropertyDescriptors())
                .map(pd -> pd.getName())
                .filter(name -> src.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }
}
