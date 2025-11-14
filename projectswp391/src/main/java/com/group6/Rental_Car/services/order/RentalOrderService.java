package com.group6.Rental_Car.services.order;

import com.group6.Rental_Car.dtos.order.OrderCreateRequest;
import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderReturnRequest;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.dtos.order.VehicleOrderHistoryResponse;
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
    List<OrderResponse> findByCustomer_UserIdOrderByCreatedAtDesc(UUID customerId);
    OrderResponse confirmPickup(UUID orderId);
    OrderResponse confirmReturn(UUID orderId, OrderReturnRequest request);
    List<OrderVerificationResponse> getPendingVerificationOrders();
    OrderResponse previewReturn(UUID orderId, Integer actualDays);
    List<VehicleOrderHistoryResponse> getOrderHistoryByVehicle(Long vehicleId);
}
