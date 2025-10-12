package com.group6.Rental_Car.services.rentalstation;

import com.group6.Rental_Car.dtos.rentalstation.RentalStationCreateRequest;
import com.group6.Rental_Car.dtos.rentalstation.RentalStationResponse;
import com.group6.Rental_Car.dtos.rentalstation.RentalStationUpdateRequest;
import com.group6.Rental_Car.entities.RentalStation;
import com.group6.Rental_Car.exceptions.ConflictException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;
@Service

public class RentalStationServiceImpl implements RentalStationService{
private final RentalStationRepository repository;
private final ModelMapper modelMapper;

    public RentalStationServiceImpl(RentalStationRepository repository, ModelMapper modelMapper) {
        this.repository = repository;
        this.modelMapper = modelMapper;
    }

    @Override
    public RentalStationResponse create(RentalStationCreateRequest req) {
        // check trung theo name + full dia chi

        boolean dup = repository.existsByNameIgnoreCaseAndCityIgnoreCaseAndDistrictIgnoreCaseAndWardIgnoreCaseAndStreetIgnoreCase(
                safe(req.getName()), safe(req.getCity()), safe(req.getDistrict()), safe(req.getWard()), safe(req.getStreet())
        );
        if (dup){
            throw new ConflictException("RentalStation already exists in this address");
        }
        RentalStation entity = new RentalStation();
        entity.setName(req.getName());
        entity.setCity(req.getCity());
        entity.setDistrict(req.getDistrict());
        entity.setWard(req.getWard());
        entity.setStreet(req.getStreet());

        entity = repository.save(entity);
        return toResponse(entity);
    }
    @Override
    public RentalStationResponse update(Integer stationId, RentalStationUpdateRequest req) {
        RentalStation entity  = repository.findById(stationId).orElseThrow(() -> new ResourceNotFoundException(" Station "));
        if (req.getName() != null && !req.getName().isEmpty()){
            entity.setName(trim(req.getName()));
        }
        if (req.getCity() != null && !req.getCity().isEmpty()){
            entity.setCity(trim(req.getCity()));
        }
        if (req.getDistrict() != null && !req.getDistrict().isEmpty()){
            entity.setDistrict(trim(req.getDistrict()));
        }
        if (req.getWard() != null && !req.getWard().isEmpty()){
            entity.setWard(trim(req.getWard()));
        }
        if (req.getStreet() != null && !req.getStreet().isEmpty()){
            entity.setStreet(trim(req.getStreet()));
        }
        boolean dup = repository.existsByNameIgnoreCaseAndCityIgnoreCaseAndDistrictIgnoreCaseAndWardIgnoreCaseAndStreetIgnoreCaseAndStationIdNot(
                safe(req.getName()), safe(req.getCity()), safe(req.getDistrict()), safe(req.getWard()), safe(req.getStreet()), entity.getStationId()
        );

        entity = repository.save(entity);
        return toResponse(entity);
    }


    @Override
    public List<RentalStationResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<RentalStationResponse> search(String q) {
        String k = (q==null || q.isBlank()) ? "": q.trim();
        if (k.isEmpty()){
            return getAll();
        }
        return repository.findByNameContainingIgnoreCaseOrCityContainingIgnoreCaseOrDistrictContainingIgnoreCaseOrWardContainingIgnoreCaseOrStreetContainingIgnoreCase(
                k,k,k,k,k
        ).stream().map(this::toResponse).toList();
    }
    private RentalStationResponse toResponse(RentalStation s) {
        RentalStationResponse dto = new RentalStationResponse();
        dto.setStationid(s.getStationId());
        dto.setName(s.getName());
        dto.setCity(s.getCity());
        dto.setDistrict(s.getDistrict());
        dto.setWard(s.getWard());
        dto.setStreet(s.getStreet());
        return dto;
    }

    private String safe(String s) {return s==null ? "" :s.trim();}
    private String trim(String s) {return s==null ? null : s.trim();}

}
