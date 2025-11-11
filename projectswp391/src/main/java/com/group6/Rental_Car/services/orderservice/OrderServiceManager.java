package com.group6.Rental_Car.services.orderservice;

import com.group6.Rental_Car.dtos.orderservice.OrderServiceCreateRequest;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceUpdateRequest;

import java.util.List;

public interface OrderServiceManager {
    OrderServiceResponse create(OrderServiceCreateRequest req);
    OrderServiceResponse update(Integer serviceId, OrderServiceUpdateRequest req);
    void delete(Integer serviceId);
    OrderServiceResponse getById(Integer serviceId);
    List<OrderServiceResponse> listAll();                // sort occurred_at DESC
    List<OrderServiceResponse> listByType(String type);
}
