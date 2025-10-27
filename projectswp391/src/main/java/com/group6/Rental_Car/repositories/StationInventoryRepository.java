package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.StationInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StationInventoryRepository extends JpaRepository<StationInventory, Integer> {

    boolean existsByStation_StationIdAndVehicle_VehicleId(Integer stationId, Long vehicleId);

    boolean existsByStation_StationIdAndVehicle_VehicleIdAndInventoryIdNot(
            Integer stationId, Long vehicleId, Integer inventoryId);

    Page<StationInventory> findByStation_StationId(Integer stationId, Pageable pageable);

    Page<StationInventory> findByVehicle_VehicleId(Long vehicleId, Pageable pageable);

    @Query("""
       select si
       from StationInventory si
       where (:stationId is null or si.station.stationId = :stationId)
         and (:vehicleId is null or si.vehicle.vehicleId = :vehicleId)
         and (:q is null or lower(si.station.name) like lower(concat('%', :q, '%'))
                         or lower(si.vehicle.plateNumber) like lower(concat('%', :q, '%')))
    """)
    Page<StationInventory> search(@Param("stationId") Integer stationId,
                                  @Param("vehicleId") Long vehicleId,
                                  @Param("q") String q,
                                  Pageable pageable);
}
