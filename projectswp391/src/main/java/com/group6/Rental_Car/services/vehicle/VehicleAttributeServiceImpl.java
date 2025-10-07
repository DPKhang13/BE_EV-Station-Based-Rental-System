package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleAttribute;
import com.group6.Rental_Car.repositories.VehicleAttributeRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VehicleAttributeServiceImpl implements VehicleAttributeService {

    private final VehicleAttributeRepository vehicleAttributeRepository;
    private final ModelMapper modelMapper;

    @Override
    public VehicleAttribute createOrUpdateAttribute(Vehicle vehicle, VehicleCreateRequest req) {
        VehicleAttribute attr = vehicleAttributeRepository.findByVehicle(vehicle)
                .orElseGet(() -> {
                    VehicleAttribute newAttr = new VehicleAttribute();
                    newAttr.setVehicle(vehicle);
                    return newAttr;
                });

        attr.setBrand(req.getBrand());
        attr.setColor(req.getColor());
        attr.setTransmission("Automatic");
        attr.setSeatCount(req.getSeatCount());
        attr.setYear(2025);
        attr.setVariant(req.getVariant());
        attr.setBatteryStatus(req.getBatteryStatus());
        attr.setBatteryCapacity(req.getBatteryCapacity());
        attr.setRangeKm(req.getRangeKm());

        return vehicleAttributeRepository.save(attr);
    }

    @Override
    public VehicleAttribute createOrUpdateAttribute(Vehicle vehicle, VehicleUpdateRequest req) {
        VehicleAttribute attr = vehicleAttributeRepository.findByVehicle(vehicle)
                .orElseGet(() -> {
                    VehicleAttribute newAttr = new VehicleAttribute();
                    newAttr.setVehicle(vehicle);
                    return newAttr;
                });

        BeanUtils.copyProperties(req, attr, getNullPropertyNames(req));
        return vehicleAttributeRepository.save(attr);
    }

    @Override
    public VehicleAttribute findByVehicle(Vehicle vehicle) {
        return vehicleAttributeRepository.findByVehicle(vehicle).orElse(null);
    }

    @Override
    public void deleteByVehicle(Vehicle vehicle) {
        vehicleAttributeRepository.findByVehicle(vehicle)
                .ifPresent(vehicleAttributeRepository::delete);
    }

    @Override
    public VehicleResponse convertToDto(Vehicle vehicle, VehicleAttribute attr) {
        VehicleResponse dto = modelMapper.map(vehicle, VehicleResponse.class);
        if (vehicle.getRentalStation() != null)
            dto.setStationId(vehicle.getRentalStation().getStationId());
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
