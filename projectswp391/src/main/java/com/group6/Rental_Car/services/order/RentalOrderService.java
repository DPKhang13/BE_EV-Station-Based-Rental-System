package com.group6.Rental_Car.services.order;

import com.group6.Rental_Car.dtos.order.*;
import com.group6.Rental_Car.dtos.verifyfile.OrderVerificationResponse;

import java.util.List;
import java.util.UUID;

public interface RentalOrderService {
    OrderResponse createOrder(OrderCreateRequest orderCreateRequest);
    OrderResponse updateOrder(UUID orderId, OrderUpdateRequest orderUpdateRequest);
    OrderResponse changeVehicle(UUID orderId, Long newVehicleId, String note);
    List<VehicleOrderHistoryResponse> getOrderHistoryByCustomer(UUID customerId);
    void deleteOrder(UUID orderId);
    List<OrderResponse> getRentalOrders();
    List<OrderSimpleResponse> getRentalOrdersSimple();
    List<OrderResponse> findByCustomer_UserId(UUID customerId);
    OrderResponse reviewReturn(UUID orderId);
    OrderResponse confirmPickup(UUID orderId);
    OrderResponse confirmReturn(UUID orderId, OrderReturnRequest request);
    List<OrderVerificationResponse> getPendingVerificationOrders();
    List<VehicleOrderHistoryResponse> getOrderHistoryByVehicle(Long vehicleId);
    List<OrderDetailCompactResponse> getCompactDetailsByVehicle(Long vehicleId);
    public OrderDetailCompactResponse updateCompactOrder(Long vehicleId, UUID orderId, CompactOrderUpdateRequest req);
    OrderResponse cancelOrder(UUID orderId, String cancellationReason);
    OrderResponse completeOrder(UUID orderId);
}
