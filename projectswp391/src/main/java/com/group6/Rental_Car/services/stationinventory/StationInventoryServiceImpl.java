package com.group6.Rental_Car.services.stationinventory;

import com.group6.Rental_Car.dtos.stationinventory.StationInventoryRequest;
import com.group6.Rental_Car.dtos.stationinventory.StationInventoryResponse;
import com.group6.Rental_Car.dtos.stationinventory.StationInventoryUpdateRequest;
import com.group6.Rental_Car.entities.RentalStation;
import com.group6.Rental_Car.entities.StationInventory;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.exceptions.ConflictException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.StationInventoryRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.data.convert.TypeMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class StationInventoryServiceImpl implements StationInventoryService {

        private final StationInventoryRepository inventoryRepo;
        private final RentalStationRepository stationRepo;
        private final VehicleRepository vehicleRepo;
        private final ModelMapper modelMapper;

        // mapping

    private TypeMap<StationInventory, StationInventoryResponse> resMap(){
        var tm = modelMapper.getTypeMap(StationInventory.class, StationInventoryResponse.class);
        if (tm == null) {
            tm = modelMapper.createTypeMap(StationInventory.class, StationInventoryResponse.class)
                    .addMapping(StationInventory::getInventoryId, StationInventoryResponse::setInventoryId)
                    .addMapping(src -> src.getStation().getStationId(), StationInventoryResponse::setStationId)
                    .addMapping(src -> src.getStation().getName(), StationInventoryResponse::setStationName)
                    .addMapping(src -> src.getVehicle().getVehicleId(), StationInventoryResponse::setVehicleId)
                    .addMapping(src -> src.getVehicle().getPlateNumber(), StationInventoryResponse::setPlateNumber);
        }
        return tm;
    }

    // helper

    private RentalStation resolveStation(Integer id){
        return stationRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Station not found: id=" + id));
    }

    private Vehicle resolveVehicle(Long id){
        return vehicleRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: id=" + id));
    }

    @Override
    public StationInventoryResponse create(StationInventoryRequest req) {

        if (inventoryRepo.existsByStation_StationIdAndVehicle_VehicleId(req.getStationId(), req.getVehicleId())) {
            throw new ConflictException("This vehicle already exists in the station inventory");
        }
        var entity = StationInventory.builder()
                .station(resolveStation(req.getStationId()))
                .vehicle(resolveVehicle(req.getVehicleId()))
                .quantity(req.getQuantity())
                .build();

        entity = inventoryRepo.save(entity);
        return resMap().map(entity);
    }

    @Override
    public StationInventoryResponse update(Integer id, StationInventoryUpdateRequest req) {

        var entity = inventoryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found: id=" + id));

        if (req.getStationId() != null) {
            entity.setStation(resolveStation(req.getStationId()));
        }
        if (req.getVehicleId() != null) {
            entity.setVehicle(resolveVehicle(req.getVehicleId()));
        }
        if (req.getQuantity() != null) {
            entity.setQuantity(req.getQuantity());
        }

        if (inventoryRepo.existsByStation_StationIdAndVehicle_VehicleIdAndInventoryIdNot(
                entity.getStation().getStationId(),
                entity.getVehicle().getVehicleId(),
                entity.getInventoryId())) {
            throw new ConflictException("This vehicle already exists in the station inventory");
        }
        entity = inventoryRepo.save(entity);
        return resMap().map(entity);
    }

    @Override
    public void delete(Integer id) {
        if(inventoryRepo.existsById(id)){
            throw new ResourceNotFoundException("Inventory not found: id=" + id);
        }
        inventoryRepo.deleteById(id);
    }

    @Override
    public Page<StationInventoryResponse> list(Pageable pageable) {
        return inventoryRepo.findAll(pageable).map(resMap()::map);
    }

    @Override
    public Page<StationInventoryResponse> search(Integer stationId, Long vehicleId, String q, Pageable pageable) {
        String kw = (q==null || q.isBlank()) ? null :q.trim();
        return inventoryRepo.search(stationId, vehicleId, kw, pageable).map(resMap()::map);
    }
}
