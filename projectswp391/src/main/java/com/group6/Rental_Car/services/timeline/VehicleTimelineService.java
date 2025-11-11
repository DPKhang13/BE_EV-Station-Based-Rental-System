package com.group6.Rental_Car.services.timeline;

import com.group6.Rental_Car.dtos.timeline.VehicleTimelineCreateRequest;
import com.group6.Rental_Car.dtos.timeline.VehicleTimelineDto;

import java.time.LocalDate;
import java.util.List;

public interface VehicleTimelineService {
    VehicleTimelineDto create(VehicleTimelineCreateRequest request);
    List<VehicleTimelineDto> getByVehicle(Long vehicleId);
    List<VehicleTimelineDto> getByVehicleAndDateRange(Long vehicleId, LocalDate from, LocalDate to);
}
