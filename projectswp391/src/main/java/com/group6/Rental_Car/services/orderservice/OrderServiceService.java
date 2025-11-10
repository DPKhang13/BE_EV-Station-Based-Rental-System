package com.group6.Rental_Car.services.orderservice;

import com.group6.Rental_Car.dtos.orderservice.OrderServiceRequest;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;

import java.util.List;
import java.util.UUID;

public interface OrderServiceService {
    OrderServiceResponse createService(OrderServiceRequest request);
    OrderServiceResponse updateService(Long serviceId, OrderServiceRequest request);
    void deleteService(Long serviceId);
    List<OrderServiceResponse> getServicesByOrder(UUID orderId);
    List<OrderServiceResponse> getServicesByVehicle(Long vehicleId);
    List<OrderServiceResponse> getServicesByStation(Integer stationId);
    List<OrderServiceResponse> getServicesByStatus(String status);
}
