package com.group6.Rental_Car.services.order;


import com.group6.Rental_Car.dtos.order.OrderCreateRequest;
import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.dtos.verifyfile.OrderVerificationResponse;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.enums.UserStatus;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.*;
import com.group6.Rental_Car.services.coupon.CouponService;
import com.group6.Rental_Car.services.pricingrule.PricingRuleService;
import com.group6.Rental_Car.services.vehicle.VehicleModelService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final IncidentRepository incidentRepository;
    private static final ZoneId VN_TZ = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String SHIFT_ALL = "ALL";
    @Override
    public OrderResponse createOrder(OrderCreateRequest request) {


        JwtUserDetails userDetails = currentUser();
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
        LocalDateTime startTime = request.getStartTime();
        if (startTime == null) {
            throw new BadRequestException("Vui lòng chọn thời gian bắt đầu thuê xe");
        }
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Thời gian bắt đầu không hợp lệ (phải là hiện tại hoặc tương lai)");
        }
        LocalDateTime endTime = startTime.plusHours(request.getPlannedHours());

        vehicle.setStatus("RESERVED");
        vehicleRepository.save(vehicle);

        RentalOrder order = modelMapper.map(request, RentalOrder.class);
        order.setCustomer(customer);
        order.setVehicle(vehicle);
        order.setCoupon(coupon);
        order.setStartTime(startTime);
        order.setEndTime(endTime);
        order.setPlannedHours(request.getPlannedHours());
        order.setPenaltyFee(BigDecimal.ZERO);
        order.setTotalPrice(totalPrice);
        order.setDepositAmount(depositAmount);
        order.setRemainingAmount(depositAmount);
        order.setStatus("PENDING");


        rentalOrderRepository.save(order);

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setVehicleId(vehicle.getVehicleId());
        response.setCouponCode(coupon != null ? coupon.getCode() : null);
        response.setTotalPrice(totalPrice);
        response.setDepositAmount(depositAmount);
        response.setRemainingAmount(remainingAmount);
        RentalStation station = vehicle.getRentalStation();
        Integer stationId = (station != null) ? station.getStationId() : null;
        response.setStationId(stationId);

        return response;
    }

    @Override
    @Transactional
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

        UUID staffId = currentUser().getUserId();
        EmployeeSchedule es = ensureTodayAllShift(staffId, order.getVehicle(), currentShift());
        es.setPickupCount(es.getPickupCount() + 1);
        employeeScheduleRepository.saveAndFlush(es);

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setVehicleId(vehicle.getVehicleId());
        if (order.getCoupon() != null)
            response.setCouponCode(order.getCoupon().getCode());
        RentalStation station = vehicle.getRentalStation();
        Integer stationId = (station != null) ? station.getStationId() : null;
        response.setStationId(stationId);
        return response;
    }

    @Override
    @Transactional
    public OrderResponse confirmReturn(UUID orderId, Integer actualHours) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        Vehicle vehicle = order.getVehicle();
        order.setEndTime(LocalDateTime.now());

        if (actualHours == null || actualHours <= 0) {
            actualHours = order.getActualHours();
        }
        if (actualHours == null || actualHours <= 0) {
            throw new BadRequestException("Không xác định được số giờ thực tế. Hãy preview trước khi xác nhận trả xe.");
        }

        order.setActualHours(actualHours);
        long hoursUsed = actualHours;

        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(
                model.getSeatCount(), model.getVariant());

        BigDecimal basePrice = pricingRuleService.calculateTotalPrice(
                rule, order.getCoupon(), order.getPlannedHours(), order.getPlannedHours());

        BigDecimal penalty = BigDecimal.ZERO;
        if (hoursUsed > order.getPlannedHours()) {
            long exceeded = hoursUsed - order.getPlannedHours();
            penalty = penalty.add(rule.getExtraHourPrice().multiply(BigDecimal.valueOf(exceeded)));
        }

        List<Incident> incidents = incidentRepository.findByVehicle_VehicleId(vehicle.getVehicleId());
        BigDecimal incidentCost = incidents.stream()
                .filter(i -> i.getCost() != null)
                .map(Incident::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        penalty = penalty.add(incidentCost);
        order.setPenaltyFee(penalty);

        BigDecimal totalPrice = basePrice.add(penalty);

        BigDecimal deposit = order.getDepositAmount() != null ? order.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal remaining = totalPrice.subtract(deposit);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        order.setTotalPrice(totalPrice);
        order.setRemainingAmount(remaining);
        order.setStatus("AWAIT_FINAL");
        rentalOrderRepository.save(order);

        vehicle.setStatus("CHECKING");
        vehicleRepository.save(vehicle);

        UUID staffId = currentUser().getUserId();
        EmployeeSchedule es = ensureTodayAllShift(staffId, order.getVehicle(), currentShift());
        es.setReturnCount(es.getReturnCount() + 1);
        employeeScheduleRepository.saveAndFlush(es);

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setVehicleId(vehicle.getVehicleId());
        response.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
        response.setPenaltyFee(penalty);
        response.setRemainingAmount(remaining);
        response.setTotalPrice(totalPrice);
        response.setActualHours(actualHours);

        RentalStation station = vehicle.getRentalStation();
        response.setStationId(station != null ? station.getStationId() : null);

        return response;
    }

    @Override
    @Transactional
    public List<OrderVerificationResponse> getPendingVerificationOrders() {

        List<RentalOrder> orders = rentalOrderRepository.findByStatusIn(
                List.of("DEPOSITED", "RENTAL", "COMPLETED", "PICKED_UP")
        );
            return orders.stream().map(order -> {
                {

                    User customer = order.getCustomer();
                    Vehicle vehicle = order.getVehicle();

                    RentalStation station = order.getVehicle().getRentalStation();
                    Integer stationId = station != null ? station.getStationId() : null;

                    String userStatusDisplay = switch (customer.getStatus()) {
                        case ACTIVE -> "ĐÃ XÁC THỰC (HỒ SƠ)";
                        case ACTIVE_PENDING -> "CHƯA XÁC THỰC";
                        default -> "KHÔNG HỢP LỆ";
                    };

                    return OrderVerificationResponse.builder()
                            .userId(customer.getUserId())
                            .orderId(order.getOrderId().toString())
                            .customerName(customer.getFullName())
                            .phone(customer.getPhone())
                            .vehicleName(vehicle.getVehicleName())
                            .plateNumber(vehicle.getPlateNumber())
                            .startTime(order.getStartTime())
                            .endTime(order.getEndTime())
                            .totalPrice(order.getTotalPrice())
                            .depositAmount(order.getDepositAmount())
                            .status(order.getStatus())
                            .userStatus(userStatusDisplay)
                            .stationId(stationId)
                            .build();

                }
            }).toList();
    }
    @Override
    public OrderResponse previewReturn(UUID orderId, Integer actualHours) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        Vehicle vehicle = order.getVehicle();

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

        order.setActualHours(actualHours);
        rentalOrderRepository.save(order);

        long hoursUsed = actualHours;

        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(
                model.getSeatCount(), model.getVariant());

        BigDecimal basePrice = pricingRuleService.calculateTotalPrice(
                rule, order.getCoupon(), order.getPlannedHours(), order.getPlannedHours());

        BigDecimal penalty = BigDecimal.ZERO;
        if (hoursUsed > order.getPlannedHours()) {
            long exceeded = hoursUsed - order.getPlannedHours();
            penalty = penalty.add(rule.getExtraHourPrice().multiply(BigDecimal.valueOf(exceeded)));
        }

        List<Incident> incidents = incidentRepository.findByVehicle_VehicleId(vehicle.getVehicleId());
        BigDecimal incidentCost = incidents.stream()
                .filter(i -> i.getCost() != null)
                .map(Incident::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        penalty = penalty.add(incidentCost);

        BigDecimal totalPrice = basePrice.add(penalty);
        BigDecimal deposit = order.getDepositAmount() != null ? order.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal remaining = totalPrice.subtract(deposit);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setVehicleId(vehicle.getVehicleId());
        response.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
        response.setPenaltyFee(penalty);
        response.setRemainingAmount(remaining);
        response.setTotalPrice(totalPrice);
        response.setActualHours(actualHours);

        RentalStation station = vehicle.getRentalStation();
        response.setStationId(station != null ? station.getStationId() : null);

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
// hàm hỗ trợ

    private String currentShift(){
        int h = LocalDateTime.now(VN_TZ).getHour();
        if (h >= 0 && h<=12) return "Morning";
        if (h >=12 && h<=18) return "Afternoon";
        return "Evening";
    }

    private EmployeeSchedule ensureTodayAllShift(UUID staffId, Vehicle vehicle, String shiftTime) {
        var today = LocalDate.now(VN_TZ);
        return employeeScheduleRepository
                .findByStaff_UserIdAndShiftDateAndShiftTime(staffId, today, shiftTime)
                .orElseGet(() ->{
                    var staff = userRepository.findById(staffId).orElseThrow(() -> new ResourceNotFoundException("Staff not found with id: " + staffId));
                    var station = staff.getRentalStation() != null ? staff.getRentalStation()
                            :  (vehicle != null ? vehicle.getRentalStation() : null);
                    if (station == null) {
                        throw new ResourceNotFoundException("Staff with id " + staffId + "is not in any station");
                    }
                    var es = EmployeeSchedule.builder()
                            .staff(staff)
                            .station(station)
                            .shiftDate(today)
                            .shiftTime(shiftTime)
                            .pickupCount(0)
                            .returnCount(0)
                            .build();
                    return employeeScheduleRepository.save(es);
                });
    }

    private JwtUserDetails currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new BadRequestException("Bạn chưa đăng nhập");
        Object p = auth.getPrincipal();
        if (p instanceof JwtUserDetails jwt) return jwt;
        throw new BadRequestException("Phiên đăng nhập không hợp lệ");
    }
}
