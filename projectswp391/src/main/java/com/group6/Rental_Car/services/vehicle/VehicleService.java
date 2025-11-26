package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleDetailResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateStatusRequest;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;


public interface VehicleService {
    VehicleResponse createVehicle(VehicleCreateRequest req, List<MultipartFile> images);
    VehicleResponse getVehicleById(Long vehicleId);
    VehicleDetailResponse getVehicleDetailById(Long vehicleId);
    VehicleResponse updateVehicle(Long vehicleId, VehicleUpdateRequest req);
    void deleteVehicle(Long vehicleId);
    List<VehicleResponse> getAllVehicles();
    VehicleResponse updateStatusVehicle(Long vehicleId, VehicleUpdateStatusRequest req);
    List<VehicleResponse> getVehiclesByStation(Integer stationId);
    List<VehicleResponse> getAvailableVehiclesByStation(Integer stationId, String carmodel);
    List<VehicleResponse> getVehiclesByCarmodel(String carmodel);
    List<VehicleResponse> getAvailableVehicles(LocalDateTime startTime, LocalDateTime endTime, Integer stationId);
    List<VehicleResponse> getSimilarAvailableVehicles(Long vehicleId);
}


