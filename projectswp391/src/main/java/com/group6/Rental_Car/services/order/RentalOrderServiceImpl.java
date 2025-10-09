package com.group6.Rental_Car.services.order;

import com.group6.Rental_Car.dtos.order.OrderCreateRequest;
import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.entities.RentalOrder;

import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;

import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;


import com.group6.Rental_Car.services.pricingrule.PricingRuleService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RentalOrderServiceImpl implements RentalOrderService {


    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalOrderRepository rentalOrderRepository;
    private final PricingRuleService pricingRuleService;

    private final ModelMapper modelMapper ;


    @Override
    public OrderResponse createOder(OrderCreateRequest orderCreateRequest) {
        JwtUserDetails userDetails = (JwtUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID customerId = userDetails.getUserId();

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Vehicle vehicle = vehicleRepository.findById(orderCreateRequest.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        PricingRule pricingRule = pricingRuleService.getPricingRuleByVehicle(vehicle);

        // Tính total price
        BigDecimal totalPrice = pricingRuleService.calculateTotalPrice(pricingRule);

        RentalOrder rentalOrder = modelMapper.map(orderCreateRequest, RentalOrder.class);
        rentalOrder.setCustomer(customer);
        rentalOrder.setVehicle(vehicle);
        rentalOrder.setTotalPrice(totalPrice);
        rentalOrder.setStatus("PENDING");

        rentalOrderRepository.save(rentalOrder);


        OrderResponse response = modelMapper.map(rentalOrder, OrderResponse.class);
        response.setVehicleId(rentalOrder.getVehicle().getVehicleId());
        response.setTotalPrice(totalPrice);

        return response;
    }
    @Override
    public OrderResponse updateOder(UUID orderId, OrderUpdateRequest orderUpdateRequest) {
        RentalOrder rentalOrder = rentalOrderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        modelMapper.map(orderUpdateRequest, rentalOrder);
        if(orderUpdateRequest.getVehicleId()!=null) {
            Vehicle vehicle = vehicleRepository.findById(orderUpdateRequest.getVehicleId()).orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
            rentalOrder.setVehicle(vehicle);

            PricingRule pricingRule = pricingRuleService.getPricingRuleByVehicle(vehicle);
            if (pricingRule == null) {
                throw new ResourceNotFoundException("Pricing rule not found for this vehicle");
            }


            BigDecimal newTotalPrice = pricingRuleService.calculateTotalPrice(pricingRule);
            rentalOrder.setTotalPrice(newTotalPrice);
        }


        rentalOrderRepository.save(rentalOrder);

        OrderResponse response = modelMapper.map(rentalOrder, OrderResponse.class);
        if (rentalOrder.getVehicle() != null) {
            response.setVehicleId(rentalOrder.getVehicle().getVehicleId());
        }
        response.setTotalPrice(rentalOrder.getTotalPrice());

        return response;
    }

    @Override
    public void deleteOder(UUID orderId) {
        // Nếu KHÔNG tồn tại -> báo lỗi
        if (!rentalOrderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }

        // Nếu tồn tại -> xóa
        rentalOrderRepository.deleteById(orderId);
    }

    @Override
    public List<OrderResponse> getRentalOrders() {
        List<RentalOrder> orders = rentalOrderRepository.findAll();
        return orders.stream()
                .map(order -> modelMapper.map(order, OrderResponse.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> findByCustomer_UserId(UUID customerId) {
        List<RentalOrder> orders = rentalOrderRepository.findByCustomer_UserId(customerId);
        return orders.stream()
                .map(order -> modelMapper.map(order, OrderResponse.class))
                .collect(Collectors.toList());

    }
}
