package com.group6.Rental_Car.services.stationinventory;

import com.group6.Rental_Car.dtos.stationinventory.StationInventoryRequest;
import com.group6.Rental_Car.dtos.stationinventory.StationInventoryResponse;
import com.group6.Rental_Car.dtos.stationinventory.StationInventoryUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StationInventoryService {
    StationInventoryResponse create(StationInventoryRequest req);
    StationInventoryResponse update(Integer id,StationInventoryUpdateRequest req);

    void delete(Integer id);

    Page<StationInventoryResponse> list(Pageable pageable);
    Page<StationInventoryResponse> search(Integer stationId, Long vehicleId, String q, Pageable pageable);
}
