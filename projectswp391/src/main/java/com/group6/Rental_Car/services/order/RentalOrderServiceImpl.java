package com.group6.Rental_Car.services.order;

import com.group6.Rental_Car.dtos.order.*;
import com.group6.Rental_Car.dtos.verifyfile.OrderVerificationResponse;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.enums.PaymentStatus;
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
import java.util.*;
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
    private final VehicleTimelineRepository vehicleTimelineRepository;

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

        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BadRequestException("Thời gian thuê không hợp lệ");
        }

        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(model.getSeatCount(), model.getVariant());

        Coupon coupon = null;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            coupon = couponService.getCouponByCode(request.getCouponCode().trim());
        }

        long rentalDays = Math.max(1, ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()));
        BigDecimal basePrice = rule.getDailyPrice().multiply(BigDecimal.valueOf(rentalDays));

        if (request.isHoliday()) {
            basePrice = rule.getHolidayPrice() != null
                    ? rule.getHolidayPrice().multiply(BigDecimal.valueOf(rentalDays))
                    : basePrice;
        }

        BigDecimal totalPrice = couponService.applyCouponIfValid(coupon, basePrice);

        // ====== TẠO ORDER ======
        RentalOrder order = new RentalOrder();
        order.setCustomer(customer);
        order.setCoupon(coupon);
        order.setTotalPrice(totalPrice);
        order.setStatus("PENDING");
        rentalOrderRepository.save(order);

        // ====== TẠO CHI TIẾT ======
        RentalOrderDetail detail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(vehicle)
                .type("RENTAL")
                .startTime(start)
                .endTime(end)
                .price(totalPrice)
                .status("PENDING")
                .build();
        rentalOrderDetailRepository.save(detail);

        // ====== CẬP NHẬT XE ======
        vehicle.setStatus("BOOKED");
        vehicleRepository.save(vehicle);

        // ====== GHI VEHICLE TIMELINE ======
        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(vehicle)
                .order(order)
                .detail(detail)
                .day(start.toLocalDate())
                .startTime(start)
                .endTime(end)
                .status("BOOKED")
                .sourceType("ORDER_RENTAL")
                .note("Xe được đặt cho đơn thuê #" + order.getOrderId())
                .updatedAt(LocalDateTime.now())
                .build();
        vehicleTimelineRepository.save(timeline);

        // ====== TRẢ RESPONSE ======
        return mapToResponse(order, detail);
    }

    @Override
    public OrderResponse updateOrder(UUID orderId, OrderUpdateRequest req) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        if (req.getStatus() != null) order.setStatus(req.getStatus());

        if (req.getCouponCode() != null && !req.getCouponCode().isBlank()) {
            Coupon coupon = couponService.getCouponByCode(req.getCouponCode().trim());
            order.setCoupon(coupon);
        }

        if (req.getNewVehicleId() != null) {
            Vehicle newVehicle = vehicleRepository.findById(req.getNewVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe mới"));

            if (!"AVAILABLE".equalsIgnoreCase(newVehicle.getStatus())) {
                throw new BadRequestException("Xe mới không khả dụng để thay thế");
            }

            RentalOrderDetail mainDetail = order.getDetails().stream()
                    .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy chi tiết thuê"));

            Vehicle oldVehicle = mainDetail.getVehicle();
            oldVehicle.setStatus("AVAILABLE");
            vehicleRepository.save(oldVehicle);

            mainDetail.setVehicle(newVehicle);
            mainDetail.setStatus("SWITCHED");
            rentalOrderDetailRepository.save(mainDetail);

            newVehicle.setStatus("BOOKED");
            vehicleRepository.save(newVehicle);

            if (req.getNote() != null) mainDetail.setDescription(req.getNote());
        }

        rentalOrderRepository.save(order);
        return mapToResponse(order, getMainDetail(order));
    }

    @Override
    @Transactional
    public void deleteOrder(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        // Lấy chi tiết chính
        RentalOrderDetail mainDetail = getMainDetail(order);

        // Nếu có detail thì update status và giải phóng xe
        if (mainDetail != null) {
            mainDetail.setStatus("FAILED");
            rentalOrderDetailRepository.save(mainDetail);

            Vehicle vehicle = mainDetail.getVehicle();
            if (vehicle != null) {
                vehicle.setStatus("AVAILABLE");
                vehicleRepository.save(vehicle);
            }
        }

        // Cuối cùng xóa order
        rentalOrderRepository.delete(order);
    }


    @Override
    public List<OrderResponse> getRentalOrders() {
        return rentalOrderRepository.findAll().stream()
                .map(order -> mapToResponse(order, getMainDetail(order)))
                .toList();
    }

    @Override
    public List<OrderResponse> findByCustomer_UserId(UUID customerId) {
        return rentalOrderRepository.findByCustomer_UserId(customerId).stream()
                .map(order -> {
                    OrderResponse res = modelMapper.map(order, OrderResponse.class);

                    // ===== Lấy detail chính (RENTAL) để gắn thêm info =====
                    RentalOrderDetail mainDetail = order.getDetails().stream()
                            .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                            .findFirst()
                            .orElse(null);

                    if (mainDetail != null) {
                        Vehicle v = mainDetail.getVehicle();
                        res.setVehicleId(v != null ? v.getVehicleId() : null);
                        res.setStartTime(mainDetail.getStartTime());
                        res.setEndTime(mainDetail.getEndTime());

                        if (v != null && v.getRentalStation() != null) {
                            res.setStationId(v.getRentalStation().getStationId());
                            res.setStationName(v.getRentalStation().getName());
                        }
                    }

                    res.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
                    res.setTotalPrice(order.getTotalPrice());
                    res.setStatus(order.getStatus());

                    return res;
                })
                .toList();
    }
    @Override
    public List<VehicleOrderHistoryResponse> getOrderHistoryByCustomer(UUID customerId) {
        return rentalOrderRepository.findByCustomer_UserId(customerId).stream()
                .flatMap(order -> order.getDetails().stream().map(detail -> {
                    Vehicle v = detail.getVehicle();
                    VehicleModel m = vehicleModelService.findByVehicle(v);
                    RentalStation s = v.getRentalStation();

                    return VehicleOrderHistoryResponse.builder()
                            .orderId(order.getOrderId())
                            .vehicleId(v.getVehicleId())
                            .plateNumber(v.getPlateNumber())

                            .stationId(s != null ? s.getStationId() : null)
                            .stationName(s != null ? s.getName() : null)

                            .brand(m != null ? m.getBrand() : null)
                            .color(m != null ? m.getColor() : null)
                            .transmission(m != null ? m.getTransmission() : null)
                            .seatCount(m != null ? m.getSeatCount() : null)
                            .year(m != null ? m.getYear() : null)
                            .variant(m != null ? m.getVariant() : null)

                            .startTime(detail.getStartTime())
                            .endTime(detail.getEndTime())
                            .status(detail.getStatus())
                            .totalPrice(detail.getPrice())

                            .build();
                }))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderResponse confirmPickup(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        // Lấy chi tiết chính
        RentalOrderDetail mainDetail = getMainDetail(order);
        if (mainDetail == null) {
            throw new BadRequestException("Không tìm thấy chi tiết đơn thuê");
        }

        // Lấy xe
        Vehicle vehicle = mainDetail.getVehicle();
        if (vehicle == null) {
            throw new BadRequestException("Không tìm thấy xe trong chi tiết đơn");
        }

        //  Cập nhật trạng thái
        order.setStatus("RENTAL");               // Đơn đang trong quá trình thuê
        mainDetail.setStatus("SUCCESS");         // Chi tiết xác nhận pickup thành công
        vehicle.setStatus("RENTAL");             // Xe đã được thuê

        //  Lưu DB
        rentalOrderDetailRepository.save(mainDetail);
        vehicleRepository.save(vehicle);
        rentalOrderRepository.save(order);

        //  Ghi lại timeline để theo dõi lịch sử
        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(vehicle)
                .order(order)
                .detail(mainDetail)
                .day(LocalDateTime.now().toLocalDate())
                .startTime(mainDetail.getStartTime())
                .endTime(mainDetail.getEndTime())
                .status("RENTAL")
                .sourceType("ORDER_PICKUP")
                .note("Xe được khách nhận cho đơn thuê #" + order.getOrderId())
                .updatedAt(LocalDateTime.now())
                .build();
        vehicleTimelineRepository.save(timeline);

        return mapToResponse(order, mainDetail);
    }


    @Override
    @Transactional
    public OrderResponse confirmReturn(UUID orderId, Integer manualActualDays) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        RentalOrderDetail mainDetail = getMainDetail(order);
        Vehicle vehicle = mainDetail.getVehicle();
        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(model.getSeatCount(), model.getVariant());

        long actualDays = manualActualDays != null ? manualActualDays
                : ChronoUnit.DAYS.between(mainDetail.getStartTime(), LocalDateTime.now());
        BigDecimal total = rule.getDailyPrice().multiply(BigDecimal.valueOf(actualDays));

        if (actualDays > ChronoUnit.DAYS.between(mainDetail.getStartTime(), mainDetail.getEndTime())) {
            long extra = actualDays - ChronoUnit.DAYS.between(mainDetail.getStartTime(), mainDetail.getEndTime());
            total = total.add(rule.getLateFeePerDay().multiply(BigDecimal.valueOf(extra)));
        }

        mainDetail.setPrice(total);
        rentalOrderDetailRepository.save(mainDetail);

        vehicle.setStatus("CHECKING");
        vehicleRepository.save(vehicle);

        order.setTotalPrice(total);
        order.setStatus("COMPLETED");
        rentalOrderRepository.save(order);

        return mapToResponse(order, mainDetail);
    }

    @Override
    public OrderResponse previewReturn(UUID orderId, Integer actualDays) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        RentalOrderDetail mainDetail = getMainDetail(order);
        Vehicle vehicle = mainDetail.getVehicle();
        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(model.getSeatCount(), model.getVariant());

        long actualDaysCount = actualDays != null
                ? actualDays
                : ChronoUnit.DAYS.between(mainDetail.getStartTime(), LocalDateTime.now());

        BigDecimal total = rule.getDailyPrice().multiply(BigDecimal.valueOf(actualDaysCount));

        if (actualDaysCount > ChronoUnit.DAYS.between(mainDetail.getStartTime(), mainDetail.getEndTime())) {
            long extra = actualDaysCount - ChronoUnit.DAYS.between(mainDetail.getStartTime(), mainDetail.getEndTime());
            total = total.add(rule.getLateFeePerDay().multiply(BigDecimal.valueOf(extra)));
        }

        //  KHÔNG cập nhật order, chỉ tạo response
        OrderResponse response = mapToResponse(order, mainDetail);
        response.setTotalPrice(total);
        response.setStatus(order.getStatus()); // Giữ nguyên trạng thái hiện tại
        return response;
    }

    @Override
    public List<OrderVerificationResponse> getPendingVerificationOrders() {
        //  Lấy tất cả đơn chưa hoàn tất
        List<RentalOrder> processingOrders = rentalOrderRepository.findAll().stream()
                .filter(o -> {
                    String s = o.getStatus().toUpperCase();
                    return s.startsWith("PENDING")     // PENDING_DEPOSIT, PENDING_FINAL, PENDING_FULL_PAYMENT
                            || s.equals("RENTAL")      // đang thuê
                            || s.equals("DEPOSITED");  // đã đặt cọc
                })
                .toList();

        return processingOrders.stream().map(order -> {
            User customer = order.getCustomer();

            // Lấy chi tiết chính
            RentalOrderDetail rentalDetail = order.getDetails().stream()
                    .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                    .findFirst()
                    .orElse(null);

            Vehicle vehicle = rentalDetail != null ? rentalDetail.getVehicle() : null;
            RentalStation station = vehicle != null ? vehicle.getRentalStation() : null;

            //  Tổng tiền từ tất cả các chi tiết orderDetail (trừ REFUND)
            BigDecimal totalFromDetails = order.getDetails() != null
                    ? order.getDetails().stream()
                    .filter(d -> !"REFUND".equalsIgnoreCase(d.getType()))
                    .map(d -> Optional.ofNullable(d.getPrice()).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : BigDecimal.ZERO;

            //  Tổng phí dịch vụ (nếu có)
            BigDecimal totalServiceCost = order.getServices() != null
                    ? order.getServices().stream()
                    .map(s -> Optional.ofNullable(s.getCost()).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : BigDecimal.ZERO;

            //  Tổng tiền thực tế
            BigDecimal totalPrice = totalFromDetails.add(totalServiceCost);

            //  Tổng đã thanh toán (chỉ SUCCESS)
            BigDecimal totalPaid = order.getPayments() != null
                    ? order.getPayments().stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                    .map(p -> Optional.ofNullable(p.getAmount()).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : BigDecimal.ZERO;

            //  Còn lại = tổng tiền - đã thanh toán
            BigDecimal remainingAmount = totalPrice.subtract(totalPaid);

            return OrderVerificationResponse.builder()
                    .userId(customer.getUserId())
                    .orderId(order.getOrderId())
                    .customerName(customer.getFullName())
                    .phone(customer.getPhone())

                    .vehicleId(vehicle != null ? vehicle.getVehicleId() : null)
                    .vehicleName(vehicle != null ? vehicle.getVehicleName() : null)
                    .plateNumber(vehicle != null ? vehicle.getPlateNumber() : null)

                    .startTime(rentalDetail != null ? rentalDetail.getStartTime() : null)
                    .endTime(rentalDetail != null ? rentalDetail.getEndTime() : null)

                    .totalPrice(totalPrice)         //  Hiển thị y như getOrderDetails
                    .totalServices(totalServiceCost)
                    .remainingAmount(remainingAmount)

                    .status(order.getStatus())
                    .userStatus(customer.getStatus().name())
                    .stationId(station != null ? station.getStationId() : null)
                    .build();
        }).toList();
    }



    @Override
    public List<VehicleOrderHistoryResponse> getOrderHistoryByVehicle(Long vehicleId) {
        return rentalOrderDetailRepository.findByVehicle_VehicleId(vehicleId).stream()
                .map(detail -> {
                    RentalOrder order = detail.getOrder();
                    Vehicle vehicle = detail.getVehicle();
                    VehicleModel model = vehicleModelService.findByVehicle(vehicle);
                    RentalStation station = vehicle.getRentalStation();

                    return VehicleOrderHistoryResponse.builder()
                            .orderId(order.getOrderId())
                            .vehicleId(vehicle.getVehicleId())
                            .plateNumber(vehicle.getPlateNumber())
                            .stationId(station != null ? station.getStationId() : null)
                            .stationName(station != null ? station.getName() : null)
                            .brand(model != null ? model.getBrand() : null)
                            .color(model != null ? model.getColor() : null)
                            .transmission(model != null ? model.getTransmission() : null)
                            .seatCount(model != null ? model.getSeatCount() : null)
                            .year(model != null ? model.getYear() : null)
                            .variant(model != null ? model.getVariant() : null)
                            .startTime(detail.getStartTime())
                            .endTime(detail.getEndTime())
                            .status(detail.getStatus())
                            .totalPrice(detail.getPrice())
                            .build();
                })
                .collect(Collectors.toList());
    }
    // ========================
    //  PRIVATE HELPERS
    // ========================
    private RentalOrderDetail getMainDetail(RentalOrder order) {
        return order.getDetails().stream()
                .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                .findFirst()
                .orElse(null);
    }

    private OrderResponse mapToResponse(RentalOrder order, RentalOrderDetail detail) {
        if (detail == null) return modelMapper.map(order, OrderResponse.class);

        OrderResponse res = modelMapper.map(order, OrderResponse.class);
        res.setStatus(order.getStatus());
        Vehicle v = detail.getVehicle();
        res.setVehicleId(v != null ? v.getVehicleId() : null);
        res.setStartTime(detail.getStartTime());
        res.setEndTime(detail.getEndTime());
        res.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
        res.setTotalPrice(order.getTotalPrice());

        if (v != null && v.getRentalStation() != null) {
            res.setStationId(v.getRentalStation().getStationId());
            res.setStationName(v.getRentalStation().getName());
        }

        return res;
    }

    private JwtUserDetails currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserDetails jwt))
            throw new BadRequestException("Phiên đăng nhập không hợp lệ");
        return jwt;
    }
}
