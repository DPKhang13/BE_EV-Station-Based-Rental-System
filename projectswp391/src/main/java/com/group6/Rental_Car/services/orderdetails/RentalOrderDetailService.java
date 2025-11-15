package com.group6.Rental_Car.services.orderdetails;

import com.group6.Rental_Car.dtos.orderdetail.OrderDetailCreateRequest;
import com.group6.Rental_Car.dtos.orderdetail.OrderDetailResponse;

import java.util.List;
import java.util.UUID;

public interface RentalOrderDetailService {

    OrderDetailResponse createDetail(OrderDetailCreateRequest request);

    OrderDetailResponse updateDetail(Long detailId, OrderDetailCreateRequest request);

    void deleteDetail(Long detailId);

    List<OrderDetailResponse> getDetailsByOrder(UUID orderId);

    List<OrderDetailResponse> getDetailsByVehicle(Long vehicleId);

    List<OrderDetailResponse> getActiveDetailsByVehicle(Long vehicleId);

    List<OrderDetailResponse> getActiveDetailsByOrder(UUID orderId);
    List<OrderDetailResponse> getDetailsByOrderStaff(UUID orderId);
}
