package com.group6.Rental_Car.services.orderservice;

import com.group6.Rental_Car.dtos.orderservice.OrderServiceCreateRequest;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;
import com.group6.Rental_Car.dtos.orderservice.ServicePriceCreateRequest;

import java.util.List;
import java.util.UUID;

public interface OrderServiceService {
    OrderServiceResponse createService(OrderServiceCreateRequest request);
    OrderServiceResponse createServicePrice(ServicePriceCreateRequest request); // Tạo dịch vụ chung (bảng giá) không cần orderId
    OrderServiceResponse updateService(Long serviceId, OrderServiceCreateRequest request);
    void deleteService(Long serviceId);
    List<OrderServiceResponse> getServicesByOrder(UUID orderId);
    List<OrderServiceResponse> getServicesByVehicle(Long vehicleId);
    List<OrderServiceResponse> getServicesByStation(Integer stationId);
    List<OrderServiceResponse> getServicesByStatus(String status);
    List<OrderServiceResponse> getPriceList(); // Bảng giá dịch vụ
}
