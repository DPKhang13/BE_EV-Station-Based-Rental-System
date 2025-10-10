package com.group6.Rental_Car.services.order;

import com.group6.Rental_Car.dtos.order.OrderCreateRequest;
import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.entities.*;

import com.group6.Rental_Car.exceptions.ResourceNotFoundException;

import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;


import com.group6.Rental_Car.services.coupon.CouponService;
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
    private final CouponService couponService;
    private final ModelMapper modelMapper;
    @Override
    public OrderResponse createOrder(OrderCreateRequest orderCreateRequest) {

        JwtUserDetails userDetails = (JwtUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID customerId = userDetails.getUserId();
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Vehicle vehicle = vehicleRepository.findById(orderCreateRequest.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        PricingRule pricingRule = pricingRuleService.getPricingRuleByVehicle(vehicle);
        Coupon coupon = null;
        if (orderCreateRequest.getCouponId() != null) {
            coupon = couponService.getCouponById(orderCreateRequest.getCouponId());
        }
        BigDecimal totalPrice = pricingRuleService.calculateTotalPrice(pricingRule, coupon);
        RentalOrder rentalOrder = modelMapper.map(orderCreateRequest, RentalOrder.class);
        rentalOrder.setCustomer(customer);
        rentalOrder.setVehicle(vehicle);
        rentalOrder.setTotalPrice(totalPrice);
        rentalOrder.setStatus("PENDING");
        rentalOrder.setCoupon(coupon);
        rentalOrderRepository.save(rentalOrder);

        OrderResponse response = modelMapper.map(rentalOrder, OrderResponse.class);
        response.setVehicleId(rentalOrder.getVehicle().getVehicleId());
        response.setTotalPrice(totalPrice);
        if (rentalOrder.getCoupon() != null) {
            response.setCouponId(rentalOrder.getCoupon().getCouponId());
        }


        return response;
    }
    @Override
    public OrderResponse updateOrder(UUID orderId, OrderUpdateRequest orderUpdateRequest) {
        RentalOrder rentalOrder = rentalOrderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        modelMapper.map(orderUpdateRequest, rentalOrder);
        if (orderUpdateRequest.getVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(orderUpdateRequest.getVehicleId()).orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
            rentalOrder.setVehicle(vehicle);

            PricingRule pricingRule = pricingRuleService.getPricingRuleByVehicle(vehicle);
            if (pricingRule == null) {
                throw new ResourceNotFoundException("Pricing rule not found for this vehicle");
            }

            Coupon coupon = null;
            if (orderUpdateRequest.getCouponId() != null) {
                coupon = couponService.getCouponById(orderUpdateRequest.getCouponId());
            }
            BigDecimal newTotalPrice = pricingRuleService.calculateTotalPrice(pricingRule, coupon);
            rentalOrder.setTotalPrice(newTotalPrice);
        }
        rentalOrderRepository.save(rentalOrder);
        OrderResponse response = modelMapper.map(rentalOrder, OrderResponse.class);
        if (rentalOrder.getVehicle() != null) {
            response.setVehicleId(rentalOrder.getVehicle().getVehicleId());
        }
        if (rentalOrder.getCoupon() != null) {
            response.setCouponId(rentalOrder.getCoupon().getCouponId());
        }
        response.setTotalPrice(rentalOrder.getTotalPrice());

        return response;
    }
    @Override
    public void deleteOrder(UUID orderId) {
        if (!rentalOrderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        rentalOrderRepository.deleteById(orderId);
    }
    @Override
    public List<OrderResponse> getRentalOrders() {
        List<RentalOrder> orders = rentalOrderRepository.findAll();
        return orders.stream().map(order -> {
            OrderResponse response = modelMapper.map(order, OrderResponse.class);
            if (order.getVehicle() != null) {
                response.setVehicleId(order.getVehicle().getVehicleId());
            }
            if (order.getCoupon() != null) {
                response.setCouponId(order.getCoupon().getCouponId());
            }
            if (order.getTotalPrice() != null) {
                response.setTotalPrice(order.getTotalPrice());
            }
            return response;
        }).collect(Collectors.toList());
    }
    @Override
    public List<OrderResponse> findByCustomer_UserId(UUID customerId) {
        List<RentalOrder> orders = rentalOrderRepository.findByCustomer_UserId(customerId);
        return orders.stream().map(order -> {
            OrderResponse response = modelMapper.map(order, OrderResponse.class);

            if (order.getVehicle() != null) {
                response.setVehicleId(order.getVehicle().getVehicleId());
            }
            if (order.getCoupon() != null) {
                response.setCouponId(order.getCoupon().getCouponId());
            }
            if (order.getTotalPrice() != null) {
                response.setTotalPrice(order.getTotalPrice());
            }

            return response;
        }).collect(Collectors.toList());
    }
}
