package com.group6.Rental_Car.services.VehicleAttribute;

import com.group6.Rental_Car.dtos.vehicleAttribute.*;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleAttribute;
import com.group6.Rental_Car.repositories.VehicleAttributeRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class VehicleAttributeServiceImpl implements VehicleAttributeService {

    private final VehicleAttributeRepository attrRepo;
    private final VehicleRepository vehicleRepo;

    @Override
    public Page<VehicleAttributeDTO> list(Long vehicleId, String plate, String q, Pageable pageable) {
        Page<VehicleAttribute> page;
        if (vehicleId != null) {
            if (q != null && !q.isBlank()) {
                page = attrRepo.findByVehicle_IdAndAttrNameContainingIgnoreCaseOrVehicle_IdAndAttrValueContainingIgnoreCase(
                        vehicleId, q.trim(), vehicleId, q.trim(), pageable);
            } else {
                page = attrRepo.findByVehicle_Id(vehicleId, pageable);
            }
        } else if (plate != null && !plate.isBlank()) {
            page = attrRepo.findByVehicle_PlateNumberContainingIgnoreCase(plate.trim(), pageable);
        } else {
            page = attrRepo.findAllBy(pageable);
        }
        return page.map(this::toDTO);
    }
    @Override
    public VehicleAttributeDTO create(VehicleAttributeRequest req) {
        Vehicle vehicle = resolveVehicle(req.getVehicleId(), req.getPlateNumber());
        Long vid = vehicle.getVehicleId();
        String name = (req.getAttrName() == null) ? null : req.getAttrName().trim();
        if (name == null || name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute name is required");
        }
        if (attrRepo.existsByVehicle_IdAndAttrNameIgnoreCase(vid, req.getAttrName().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute name already exists for this vehicle");
        }
        VehicleAttribute entity = VehicleAttribute.builder()
                .vehicle(vehicle)
                .attrName(req.getAttrName().trim())
                .attrValue(req.getAttrValue())
                .build();

        try{
            entity=attrRepo.save(entity);
        }catch (DataIntegrityViolationException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate attribute for this vehicle");
        }
        return toDTO(entity);
    }

    @Override
    public VehicleAttributeDTO update(Long id, VehicleAttributeRequest req) {
        VehicleAttribute entity = attrRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attribute not found"));

        if (req.getAttrName() != null && !req.getAttrName().isBlank()) {
            String newName = req.getAttrName().trim();
            Long vid = entity.getVehicle().getVehicleId();
            if (!newName.equalsIgnoreCase(entity.getAttrName())
                    && attrRepo.existsByVehicle_IdAndAttrNameIgnoreCase(vid, newName)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute name already exists for this vehicle");
            }
            entity.setAttrName(newName);
        }
        if (req.getAttrValue() != null){
            entity.setAttrValue(req.getAttrValue());
        }
        try{
            entity=attrRepo.save(entity);
        }catch (DataIntegrityViolationException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate attribute for this vehicle");
        }
        return toDTO(entity);
    }

    @Override
    public void delete(Long id) {
        if (!attrRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attribute not found");
        }
        attrRepo.deleteById(id);
    }


    @Override
    public BulkUpsertResult bulkUpsert(BulkUpsertRequest req) { // <-- nhận BulkUpsertRequest
        int created = 0, updated = 0;

        for (BulkUpsertItem item : req.getItems()) {            // <-- giờ dùng getItems() OK
            Vehicle v  = resolveVehicle(item.getVehicleId(), item.getPlateNumber());
            Long vid   = v.getVehicleId();                      // nếu Vehicle dùng getId() thì đổi tại đây
            String name = item.getAttrName().trim();

            VehicleAttribute entity =
                    attrRepo.findByVehicle_IdAndAttrNameIgnoreCase(vid, name).orElse(null);

            if (entity == null) {
                entity = VehicleAttribute.builder()
                        .vehicle(v)
                        .attrName(name)
                        .attrValue(item.getAttrValue())
                        .build();
                attrRepo.save(entity);
                created++;
            } else {
                entity.setAttrValue(item.getAttrValue());
                attrRepo.save(entity);
                updated++;
            }
        }
        return new BulkUpsertResult(created, updated);
    }
    private VehicleAttributeDTO toDTO(VehicleAttribute a) {
        return VehicleAttributeDTO.builder().id(a.getAttrId())
                .vehicleId(a.getVehicle().getVehicleId())
                .plateNumber(a.getVehicle().getPlateNumber())
                .attrName(a.getAttrName())
                .attrValue(a.getAttrValue())
                .build();
    }
    private Vehicle resolveVehicle(Long vehicleId, String plateNumber) {
        if (vehicleId != null) {
            return vehicleRepo.findById(vehicleId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle not found: id=" + vehicleId));
        }
        if (plateNumber != null && !plateNumber.isBlank()) {
            return vehicleRepo.findByPlateNumber(plateNumber.trim()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plate number not found: plateNumber=" + plateNumber));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VehicleId or Plate number is required");

    }
}

