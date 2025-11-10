package com.group6.Rental_Car.services.order;

import com.group6.Rental_Car.dtos.order.OrderCreateRequest;
import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.dtos.order.VehicleOrderHistoryResponse;
import com.group6.Rental_Car.dtos.verifyfile.OrderVerificationResponse;
import com.group6.Rental_Car.entities.*;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RentalOrderServiceImpl implements RentalOrderService {

    private final RentalOrderRepository rentalOrderRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleModelService vehicleModelService;
    private final PricingRuleService pricingRuleService;
    private final CouponService couponService;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    // ========================
    // 1️⃣ TẠO ĐƠN THUÊ
    // ========================
    @Override
    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {

        JwtUserDetails jwt = currentUser();
        User customer = userRepository.findById(jwt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        if (!"AVAILABLE".equalsIgnoreCase(vehicle.getStatus())) {
            throw new BadRequestException("Xe hiện không sẵn sàng để thuê (" + vehicle.getStatus() + ")");
        }

        // Xác định ngày thuê
        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BadRequestException("Thời gian thuê không hợp lệ");
        }

        // Tìm rule giá theo xe
        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(model.getSeatCount(), model.getVariant());

        // Coupon (nếu có)
        Coupon coupon = null;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            coupon = couponService.getCouponByCode(request.getCouponCode().trim());
        }

        // Tính số ngày thuê (làm tròn lên)
        long rentalDays = Math.max(1, ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()));
        BigDecimal basePrice = rule.getDailyPrice().multiply(BigDecimal.valueOf(rentalDays));

        // Áp dụng giá lễ nếu có
        if (request.isHoliday()) {
            basePrice = rule.getHolidayPrice() != null
                    ? rule.getHolidayPrice().multiply(BigDecimal.valueOf(rentalDays))
                    : basePrice;
        }

        // Áp dụng coupon nếu có
        BigDecimal totalPrice = couponService.applyCouponIfValid(coupon, basePrice);

        // Tạo RentalOrder tổng
        RentalOrder order = new RentalOrder();
        order.setCustomer(customer);
        order.setCoupon(coupon);
        order.setTotalPrice(totalPrice);
        order.setStatus("PENDING");
        rentalOrderRepository.save(order);

        // Tạo detail RENTAL chính
        RentalOrderDetail detail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(vehicle)
                .type("RENTAL")
                .startTime(start)
                .endTime(end)
                .price(totalPrice)
                .status("confirmed")
                .build();
        rentalOrderDetailRepository.save(detail);

        // Cập nhật trạng thái xe
        vehicle.setStatus("BOOKED");
        vehicleRepository.save(vehicle);

        // Map ra response
        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setVehicleId(vehicle.getVehicleId());
        response.setCouponCode(coupon != null ? coupon.getCode() : null);
        response.setTotalPrice(totalPrice);
        return response;
    }

    // ========================
    // 2️⃣ CẬP NHẬT ĐƠN
    // ========================
    @Override
    public OrderResponse updateOrder(UUID orderId, OrderUpdateRequest req) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        // Cập nhật status
        if (req.getStatus() != null) order.setStatus(req.getStatus());

        // Cập nhật coupon
        if (req.getCouponCode() != null && !req.getCouponCode().isBlank()) {
            Coupon coupon = couponService.getCouponByCode(req.getCouponCode().trim());
            order.setCoupon(coupon);
        }

        // Nếu có yêu cầu đổi xe
        if (req.getNewVehicleId() != null) {
            Vehicle newVehicle = vehicleRepository.findById(req.getNewVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe mới"));

            if (!"AVAILABLE".equalsIgnoreCase(newVehicle.getStatus())) {
                throw new BadRequestException("Xe mới không khả dụng để thay thế");
            }

            // Lấy detail hiện tại (xe cũ)
            RentalOrderDetail mainDetail = order.getDetails().stream()
                    .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy chi tiết thuê"));

            Vehicle oldVehicle = mainDetail.getVehicle();
            oldVehicle.setStatus("AVAILABLE");
            vehicleRepository.save(oldVehicle);

            // Cập nhật detail sang xe mới
            mainDetail.setVehicle(newVehicle);
            mainDetail.setStatus("switched");
            rentalOrderDetailRepository.save(mainDetail);

            // Cập nhật xe mới
            newVehicle.setStatus("BOOKED");
            vehicleRepository.save(newVehicle);

            // Có thể ghi chú lý do đổi
            if (req.getNote() != null) {
                mainDetail.setDescription(req.getNote());
            }
        }

        rentalOrderRepository.save(order);
        return modelMapper.map(order, OrderResponse.class);
    }


    @Override
    public void deleteOrder(UUID orderId) {
        if (!rentalOrderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order not found");
        }
        rentalOrderRepository.deleteById(orderId);
    }

    @Override
    public List<VehicleOrderHistoryResponse> getOrderHistoryByCustomer(UUID customerId) {
        List<RentalOrder> orders = rentalOrderRepository.findByCustomer_UserId(customerId);

        return orders.stream()
                .flatMap(order -> order.getDetails().stream().map(detail -> {
                    Vehicle v = detail.getVehicle();
                    VehicleModel m = vehicleModelService.findByVehicle(v);
                    RentalStation s = v.getRentalStation();
                    return VehicleOrderHistoryResponse.builder()
                            .orderId(order.getOrderId())
                            .vehicleId(v.getVehicleId())
                            .plateNumber(v.getPlateNumber())
                            .stationName(s != null ? s.getName() : null)
                            .startTime(detail.getStartTime())
                            .endTime(detail.getEndTime())
                            .status(detail.getStatus())
                            .totalPrice(detail.getPrice())
                            .variant(m != null ? m.getVariant() : null)
                            .build();
                }))
                .collect(Collectors.toList());
    }

    // ========================
    // 5️⃣ XỬ LÝ PICKUP / RETURN
    // ========================
    @Override
    @Transactional
    public OrderResponse confirmPickup(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        order.setStatus("RENTAL");
        rentalOrderRepository.save(order);
        return modelMapper.map(order, OrderResponse.class);
    }

    @Override
    @Transactional
    public OrderResponse confirmReturn(UUID orderId, Integer manualActualDays) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        RentalOrderDetail mainDetail = order.getDetails().stream()
                .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Không có chi tiết thuê để xác nhận trả"));

        Vehicle vehicle = mainDetail.getVehicle();
        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(model.getSeatCount(), model.getVariant());

        long actualDays = manualActualDays != null ? manualActualDays
                : ChronoUnit.DAYS.between(mainDetail.getStartTime(), LocalDateTime.now());
        BigDecimal total = rule.getDailyPrice().multiply(BigDecimal.valueOf(actualDays));

        // Tính phí trả trễ
        if (actualDays > ChronoUnit.DAYS.between(mainDetail.getStartTime(), mainDetail.getEndTime())) {
            long extra = actualDays - ChronoUnit.DAYS.between(mainDetail.getStartTime(), mainDetail.getEndTime());
            total = total.add(rule.getLateFeePerDay().multiply(BigDecimal.valueOf(extra)));
        }

        mainDetail.setStatus("done");
        mainDetail.setPrice(total);
        rentalOrderDetailRepository.save(mainDetail);

        vehicle.setStatus("AVAILABLE");
        vehicleRepository.save(vehicle);

        order.setTotalPrice(total);
        order.setStatus("COMPLETED");
        rentalOrderRepository.save(order);

        return modelMapper.map(order, OrderResponse.class);
    }

    @Override
    public List<OrderVerificationResponse> getPendingVerificationOrders() {
        throw new UnsupportedOperationException("Pending verification flow chưa áp dụng cho cấu trúc mới");
    }

    @Override
    public OrderResponse previewReturn(UUID orderId, Integer actualDays) {
        return confirmReturn(orderId, actualDays);
    }

    @Override
    public List<VehicleOrderHistoryResponse> getOrderHistoryByVehicle(Long vehicleId) {
        throw new UnsupportedOperationException("TODO: implement later");
    }

    @Override
    public List<OrderResponse> getRentalOrders() {
        return rentalOrderRepository.findAll().stream()
                .map(o -> modelMapper.map(o, OrderResponse.class))
                .toList();
    }

    @Override
    public List<OrderResponse> findByCustomer_UserId(UUID customerId) {
        return rentalOrderRepository.findByCustomer_UserId(customerId)
                .stream().map(o -> modelMapper.map(o, OrderResponse.class))
                .toList();
    }

    private JwtUserDetails currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserDetails jwt))
            throw new BadRequestException("Phiên đăng nhập không hợp lệ");
        return jwt;
    }
}
