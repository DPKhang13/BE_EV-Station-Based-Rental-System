package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleModel;
import com.group6.Rental_Car.repositories.VehicleModelRepository;
import com.group6.Rental_Car.services.pricingrule.PricingRuleService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VehicleModelServiceImpl implements VehicleModelService {

    private final VehicleModelRepository vehicleModelRepository;
    private final ModelMapper modelMapper;
    private final PricingRuleService pricingRuleService;

    @Override
    public VehicleModel createModel(Vehicle vehicle, VehicleCreateRequest req) {
        VehicleModel attr = vehicleModelRepository.findByVehicle(vehicle)
                .orElseGet(() -> {
                    VehicleModel newAttr = new VehicleModel();
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
        attr.setCarmodel(req.getCarmodel());
        PricingRule rule = pricingRuleService.getPricingRuleByCarmodel(req.getCarmodel());
        attr.setPricingRule(rule);
        return vehicleModelRepository.save(attr);
    }

    @Override
    public VehicleModel updateModel(Vehicle vehicle, VehicleUpdateRequest req) {
        VehicleModel attr = vehicleModelRepository.findByVehicle(vehicle)
                .orElseGet(() -> {
                    VehicleModel newAttr = new VehicleModel();
                    newAttr.setVehicle(vehicle);
                    return newAttr;
                });

        BeanUtils.copyProperties(req, attr, getNullPropertyNames(req));
        return vehicleModelRepository.save(attr);
    }

    @Override
    public VehicleModel findByVehicle(Vehicle vehicle) {
        return vehicleModelRepository.findByVehicle(vehicle).orElse(null);
    }

    @Override
    public void deleteByVehicle(Vehicle vehicle) {
        vehicleModelRepository.findByVehicle(vehicle)
                .ifPresent(vehicleModelRepository::delete);
    }

    @Override
    public VehicleResponse convertToDto(Vehicle vehicle, VehicleModel attr) {
        VehicleResponse dto = modelMapper.map(vehicle, VehicleResponse.class);
        if (vehicle.getRentalStation() != null) {
            dto.setStationId(vehicle.getRentalStation().getStationId());
            dto.setStationName(vehicle.getRentalStation().getName());
        }
        // Map imageUrl từ vehicle
        dto.setImageUrl(vehicle.getImageUrl());
        
        if (attr != null) {
            dto.setBrand(attr.getBrand());
            dto.setColor(attr.getColor());
            dto.setTransmission(attr.getTransmission());
            dto.setSeatCount(attr.getSeatCount());
            dto.setYear(attr.getYear());
            dto.setVariant(attr.getVariant());
            dto.setBatteryStatus(attr.getBatteryStatus());
            dto.setBatteryCapacity(attr.getBatteryCapacity());
            dto.setCarmodel(attr.getCarmodel());
            dto.setPricingRuleId(
                    attr.getPricingRule() != null ? attr.getPricingRule().getPricingRuleId() : null
            );
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
