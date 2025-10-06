package com.group6.Rental_Car.services.VehicleAttribute;

import com.group6.Rental_Car.dtos.vehicleAttribute.*;
import com.group6.Rental_Car.entities.VehicleAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VehicleAttributeService {
    Page<VehicleAttributeDTO> list(Long vehicleId,String plate, String q, Pageable pageable);
    VehicleAttributeDTO create(VehicleAttributeRequest req);
    VehicleAttributeDTO update(Long id, VehicleAttributeRequest req);
    void delete(Long id);
    BulkUpsertResult bulkUpsert(BulkUpsertRequest req);
}
