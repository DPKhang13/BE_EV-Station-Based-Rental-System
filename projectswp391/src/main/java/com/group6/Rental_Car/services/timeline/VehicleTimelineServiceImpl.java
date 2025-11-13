package com.group6.Rental_Car.services.timeline;

import com.group6.Rental_Car.dtos.timeline.VehicleTimelineCreateRequest;
import com.group6.Rental_Car.dtos.timeline.VehicleTimelineDto;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.repositories.*;
import com.group6.Rental_Car.services.timeline.VehicleTimelineService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleTimelineServiceImpl implements VehicleTimelineService {

    private final VehicleTimelineRepository vehicleTimelineRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalOrderRepository rentalOrderRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;
    private final OrderServiceRepository orderServiceRepository;
    private final ModelMapper modelMapper;

    @Override
    public VehicleTimelineDto create(VehicleTimelineCreateRequest req) {
        Vehicle vehicle = vehicleRepository.findById(req.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(vehicle)
                .order(req.getOrderId() != null ? rentalOrderRepository.findById(req.getOrderId()).orElse(null) : null)
                .detail(req.getDetailId() != null ? rentalOrderDetailRepository.findById(req.getDetailId()).orElse(null) : null)
                .service(req.getServiceId() != null ? orderServiceRepository.findById(req.getServiceId()).orElse(null) : null)
                .sourceType(req.getSourceType())
                .status(req.getStatus())
                .note(req.getNote())
                .day(req.getStartTime().toLocalDate())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .build();

        return modelMapper.map(vehicleTimelineRepository.save(timeline), VehicleTimelineDto.class);
    }

    @Override
    public List<VehicleTimelineDto> getByVehicle(Long vehicleId) {
        return vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId)
                .stream()
                .map(t -> {
                    VehicleTimelineDto dto = modelMapper.map(t, VehicleTimelineDto.class);
                    dto.setVehicleId(t.getVehicle().getVehicleId());
                    dto.setVehicleName(t.getVehicle().getVehicleName());
                    dto.setOrderId(t.getOrder() != null ? t.getOrder().getOrderId() : null);
                    dto.setDetailId(t.getDetail() != null ? t.getDetail().getDetailId() : null);
                    dto.setServiceId(t.getService() != null ? t.getService().getServiceId() : null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<VehicleTimelineDto> getByVehicleAndDateRange(Long vehicleId, LocalDate from, LocalDate to) {
        return vehicleTimelineRepository.findByVehicle_VehicleIdAndDayBetween(vehicleId, from, to)
                .stream()
                .map(t -> {
                    VehicleTimelineDto dto = modelMapper.map(t, VehicleTimelineDto.class);
                    dto.setVehicleId(t.getVehicle().getVehicleId());
                    dto.setVehicleName(t.getVehicle().getVehicleName());
                    dto.setOrderId(t.getOrder() != null ? t.getOrder().getOrderId() : null);
                    dto.setDetailId(t.getDetail() != null ? t.getDetail().getDetailId() : null);
                    dto.setServiceId(t.getService() != null ? t.getService().getServiceId() : null);
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
