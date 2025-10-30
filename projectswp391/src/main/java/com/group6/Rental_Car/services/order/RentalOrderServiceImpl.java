package com.group6.Rental_Car.services.order;

import com.group6.Rental_Car.dtos.order.OrderCreateRequest;
import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.dtos.verifyfile.OrderVerificationResponse;
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
            throw new ResourceNotFoundException("Không tìm thấy model cho xe ID = " + vehicle.getVehicleId());
        }

        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(
                model.getSeatCount(), model.getVariant());

        Coupon coupon = null;
        if (request.getCouponCode() != null && !request.getCouponCode().trim().isEmpty()) {
            coupon = couponService.getCouponByCode(request.getCouponCode().trim());
        }

        BigDecimal totalPrice = pricingRuleService.calculateTotalPrice(
                rule, coupon, request.getPlannedHours(), request.getPlannedHours());


        BigDecimal depositAmount = BigDecimal.ZERO;
        BigDecimal remainingAmount = totalPrice;


        vehicle.setStatus("RESERVED");
        vehicleRepository.save(vehicle);

        RentalOrder order = modelMapper.map(request, RentalOrder.class);
        order.setCustomer(customer);
        order.setVehicle(vehicle);
        order.setCoupon(coupon);
        order.setPlannedHours(request.getPlannedHours());
        order.setPenaltyFee(BigDecimal.ZERO);
        order.setTotalPrice(totalPrice);
        order.setDepositAmount(depositAmount);
        order.setRemainingAmount(depositAmount);
        order.setStatus("PENDING_PAYMENT");

        rentalOrderRepository.save(order);

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setVehicleId(vehicle.getVehicleId());
        response.setCouponCode(coupon != null ? coupon.getCode() : null);
        response.setTotalPrice(totalPrice);
        response.setDepositAmount(depositAmount);
        response.setRemainingAmount(remainingAmount);

        return response;
    }

    @Override
    public OrderResponse confirmPickup(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        if (!"DEPOSITED".equalsIgnoreCase(order.getStatus())
                && !"PAYMENT_SUCCESS".equalsIgnoreCase(order.getStatus())) {
            throw new BadRequestException("Đơn này không thể xác nhận nhận xe khi trạng thái là: " + order.getStatus());
        }

        Vehicle vehicle = order.getVehicle();
        vehicle.setStatus("RENTAL");
        vehicleRepository.save(vehicle);

        order.setStatus("RENTAL");
        order.setStartTime(LocalDateTime.now());
        rentalOrderRepository.save(order);

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setVehicleId(vehicle.getVehicleId());
        if (order.getCoupon() != null)
            response.setCouponCode(order.getCoupon().getCode());

        return response;
    }

    @Override
    public OrderResponse confirmReturn(UUID orderId, Integer actualHours) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        Vehicle vehicle = order.getVehicle();
        order.setEndTime(LocalDateTime.now());

        if (actualHours == null || actualHours <= 0) {
            int planned = order.getPlannedHours() != null ? order.getPlannedHours() : 12;
            int min, max;

            if (planned <= 24) {
                min = Math.max(1, planned - 1);
                max = planned + 14;
            } else {
                min = Math.max(1, planned - 10);
                max = planned + 10;
            }

            actualHours = (int) (Math.random() * (max - min + 1)) + min;
        }

        long hoursUsed = actualHours;
        order.setActualHours((int) hoursUsed);

        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(
                model.getSeatCount(), model.getVariant());

        BigDecimal totalPrice = pricingRuleService.calculateTotalPrice(
                rule, order.getCoupon(), order.getPlannedHours(), hoursUsed);

        order.setTotalPrice(totalPrice);

        if (hoursUsed > order.getPlannedHours()) {
            long exceeded = hoursUsed - order.getPlannedHours();
            BigDecimal penalty = rule.getExtraHourPrice().multiply(BigDecimal.valueOf(exceeded));
            order.setPenaltyFee(penalty);
        } else {
            order.setPenaltyFee(BigDecimal.ZERO);
        }

        BigDecimal deposit = order.getDepositAmount() != null ? order.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal remaining = totalPrice.subtract(deposit);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        order.setRemainingAmount(remaining);
        order.setStatus("AWAIT_FINAL"); // gọn và đúng flow
        rentalOrderRepository.save(order);

        vehicle.setStatus("CHECKING");
        vehicleRepository.save(vehicle);

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setVehicleId(vehicle.getVehicleId());
        response.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
        response.setRemainingAmount(remaining);
        response.setTotalPrice(totalPrice);

        return response;
    }

    @Override
    public List<OrderVerificationResponse> getPendingVerificationOrders() {
        List<RentalOrder> orders = rentalOrderRepository.findByStatus("DEPOSITED");

        return orders.stream().map(order -> OrderVerificationResponse.builder()
                        .orderId(order.getOrderId().toString())
                        .customerName(order.getCustomer().getFullName())
                        .phone(order.getCustomer().getPhone())
                        .vehicleName(order.getVehicle().getVehicleName())
                        .plateNumber(order.getVehicle().getPlateNumber())
                        .startTime(order.getStartTime())
                        .endTime(order.getEndTime())
                        .totalPrice(order.getTotalPrice())
                        .depositAmount(order.getDepositAmount())
                        .status(order.getStatus())
                        .build())
                .toList();
    }


    // ==============================================================
    //                   CÁC HÀM CRUD CÒN LẠI
    // ==============================================================
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
                    response.setVehicleId(order.getVehicle() != null ? order.getVehicle().getVehicleId() : null);
                    response.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> findByCustomer_UserId(UUID customerId) {
        return rentalOrderRepository.findByCustomer_UserId(customerId).stream()
                .map(order -> {
                    OrderResponse response = modelMapper.map(order, OrderResponse.class);
                    response.setVehicleId(order.getVehicle() != null ? order.getVehicle().getVehicleId() : null);
                    response.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
                    return response;
                })
                .collect(Collectors.toList());
    }
}
