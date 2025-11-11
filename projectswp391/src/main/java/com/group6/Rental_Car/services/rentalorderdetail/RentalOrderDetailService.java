package com.group6.Rental_Car.services.rentalorderdetail;

import com.group6.Rental_Car.dtos.rentalorderdetail.RentalOrderDetailCreateRequest;
import com.group6.Rental_Car.dtos.rentalorderdetail.RentalOrderDetailResponse;
import com.group6.Rental_Car.dtos.rentalorderdetail.RentalOrderDetailUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface RentalOrderDetailService {

    RentalOrderDetailResponse create(RentalOrderDetailCreateRequest req);

    RentalOrderDetailResponse update(Long detailId, RentalOrderDetailUpdateRequest req);

    void delete(Long detailId);

    RentalOrderDetailResponse getById(Long detailId);

    List<RentalOrderDetailResponse> listAll();

    List<RentalOrderDetailResponse> listByOrder(UUID orderId);

    List<RentalOrderDetailResponse> listByVehicle(Long vehicleId);

    List<RentalOrderDetailResponse> listByStatus(String status);

    List<RentalOrderDetailResponse> listByType(String type);
}
