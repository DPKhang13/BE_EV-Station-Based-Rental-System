package com.group6.Rental_Car.services.order;

import com.group6.Rental_Car.dtos.order.OrderCreateRequest;
import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import com.group6.Rental_Car.services.coupon.CouponService;
import com.group6.Rental_Car.services.pricingrule.PricingRuleService;
import com.group6.Rental_Car.services.vehicle.VehicleModelService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
    private final VehicleModelService vehicleModelService;

    @Override
    public OrderResponse createOrder(OrderCreateRequest request) {

        JwtUserDetails userDetails =
                (JwtUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID customerId = userDetails.getUserId();

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        if (!"available".equalsIgnoreCase(vehicle.getStatus())) {
            throw new BadRequestException("Xe hiện không sẵn sàng để thuê (" + vehicle.getStatus() + ")");
        }
        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        if (model == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin model cho xe ID = " + vehicle.getVehicleId());
        }

        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(
                model.getSeatCount(), model.getVariant());

        Coupon coupon = null;
        String couponCode = request.getCouponCode();

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            coupon = couponService.getCouponByCode(couponCode.trim());
        }
        BigDecimal totalPrice = pricingRuleService.calculateTotalPrice(
                rule, coupon, request.getPlannedHours(), request.getActualHours());
        vehicle.setStatus("RESERVED");
        vehicleRepository.save(vehicle);
        RentalOrder order = modelMapper.map(request, RentalOrder.class);
        order.setCustomer(customer);
        order.setVehicle(vehicle);
        order.setCoupon(coupon);
        order.setPlannedHours(request.getPlannedHours());
        order.setActualHours(request.getActualHours());
        order.setPenaltyFee(BigDecimal.ZERO);
        order.setTotalPrice(totalPrice);
        order.setStatus("PENDING");

        rentalOrderRepository.save(order);

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setVehicleId(vehicle.getVehicleId());
        response.setTotalPrice(totalPrice);
        if (coupon != null) response.setCouponCode(coupon.getCode());

        return response;
    }

    public OrderResponse confirmPickup(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        if (!"PAYMENT_SUCCESS".equalsIgnoreCase(order.getStatus())
                &&  !"PAYMENT_FAILED".equalsIgnoreCase(order.getStatus())
                && !"PENDING".equalsIgnoreCase(order.getStatus())) {
            throw new BadRequestException("Đơn này không thể xác nhận pickup khi trạng thái là: " + order.getStatus());
        }
        Vehicle vehicle = order.getVehicle();
        vehicle.setStatus("RENTAL");
        vehicleRepository.save(vehicle);
        order.setStartTime(LocalDateTime.now());
        rentalOrderRepository.save(order);

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        if (order.getVehicle() != null)
            response.setVehicleId(order.getVehicle().getVehicleId());
        if (order.getCoupon() != null)
            response.setCouponCode(order.getCoupon().getCode());

        return response;
    }

    public OrderResponse confirmReturn(UUID orderId, Integer ActualHours) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));
        Vehicle vehicle = order.getVehicle();
        order.setEndTime(LocalDateTime.now());
        long actualHours;
        if (ActualHours != null && ActualHours > 0) {
            actualHours = ActualHours;
        } else {
            actualHours = Duration.between(order.getStartTime(), order.getEndTime()).toHours();
        }

        order.setActualHours((int) actualHours);

        VehicleModel model = vehicleModelService.findByVehicle(order.getVehicle());
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(
                model.getSeatCount(), model.getVariant());

        BigDecimal totalPrice = pricingRuleService.calculateTotalPrice(
                rule, order.getCoupon(), order.getPlannedHours(), actualHours);
        order.setTotalPrice(totalPrice);

        if (actualHours > order.getPlannedHours()) {
            long exceeded = actualHours - order.getPlannedHours();
            BigDecimal penalty = rule.getExtraHourPrice().multiply(BigDecimal.valueOf(exceeded));
            order.setPenaltyFee(penalty);
            order.setTotalPrice(order.getTotalPrice().add(penalty));
        } else {
            order.setPenaltyFee(BigDecimal.ZERO);
        }
        rentalOrderRepository.save(order);

        vehicle.setStatus("CHECKING");
        vehicleRepository.save(vehicle);

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        if (order.getVehicle() != null)
            response.setVehicleId(order.getVehicle().getVehicleId());
        if (order.getCoupon() != null)
            response.setCouponCode(order.getCoupon().getCode());

        return response;
    }

    @Override
    public OrderResponse updateOrder(UUID orderId, OrderUpdateRequest request) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        modelMapper.map(request, order);
        rentalOrderRepository.save(order);

        return modelMapper.map(order, OrderResponse.class);
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
        return rentalOrderRepository.findAll().stream()
                .map(order -> {
                    OrderResponse response = modelMapper.map(order, OrderResponse.class);
                    if (order.getVehicle() != null)
                        response.setVehicleId(order.getVehicle().getVehicleId());
                    if (order.getCoupon() != null)
                        response.setCouponCode(order.getCoupon().getCode());
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> findByCustomer_UserId(UUID customerId) {
        return rentalOrderRepository.findByCustomer_UserId(customerId).stream()
                .map(order -> {
                    OrderResponse response = modelMapper.map(order, OrderResponse.class);
                    if (order.getVehicle() != null)
                        response.setVehicleId(order.getVehicle().getVehicleId());
                    if (order.getCoupon() != null)
                        response.setCouponCode(order.getCoupon().getCode());
                    return response;
                })
                .collect(Collectors.toList());
    }
}
