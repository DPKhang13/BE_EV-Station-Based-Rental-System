package com.group6.Rental_Car.services.order;

import com.group6.Rental_Car.dtos.order.*;
import com.group6.Rental_Car.dtos.verifyfile.OrderVerificationResponse;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ConflictException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.*;
import com.group6.Rental_Car.services.coupon.CouponService;
import com.group6.Rental_Car.services.pricingrule.PricingRuleService;
import com.group6.Rental_Car.services.vehicle.VehicleModelService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import com.group6.Rental_Car.utils.UserDocsGuard;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Comparator;
import java.util.Random;
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
    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final PhotoRepository photoRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationRepository notificationRepository;
    private final VehicleModelRepository vehicleModelRepository;
    @Override
    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {

        JwtUserDetails jwt = currentUser();
        User customer = userRepository.findById(jwt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));


        UserDocsGuard.assertHasDocs(
                customer.getUserId(),
                (uid, type) -> photoRepository.existsByUser_UserIdAndTypeIgnoreCase(uid, type)
        );

        // Kiểm tra khách hàng đã có đơn đang xử lý chưa
        List<RentalOrder> existingOrders = rentalOrderRepository.findByCustomer_UserId(customer.getUserId());
        boolean hasActiveOrder = existingOrders.stream()
                .anyMatch(order -> {
                    String status = order.getStatus();
                    if (status == null) return false;
                    String upperStatus = status.toUpperCase();
                    return upperStatus.equals("DEPOSITED") 
                            || upperStatus.equals("PENDING")
                            || upperStatus.equals("RENTAL")
                            || upperStatus.startsWith("PENDING");
                });
        
        if (hasActiveOrder) {
            throw new BadRequestException("Bạn đã có đơn đang xử lý hoặc đang thuê. Vui lòng hoàn tất đơn hiện tại trước khi đặt xe mới.");
        }

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));


        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BadRequestException("Thời gian thuê không hợp lệ");
        }

        // Kiểm tra xem có booking trùng lặp không (nếu có thì KHÔNG ĐẶT)
        if (hasOverlappingActiveBooking(vehicle.getVehicleId(), start, end)) {
            throw new BadRequestException("Xe đã được đặt trong khoảng thời gian này...");
        }

        System.out.println(" [createOrder] Xe " + vehicle.getVehicleId() + " có thể đặt từ " + start + " đến " + end);
        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleByCarmodel(model.getCarmodel());

        Coupon coupon = null;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            coupon = couponService.getCouponByCode(request.getCouponCode().trim());
        }

        // Tính giá dựa trên từng ngày (tự động tính giá cuối tuần)
        BigDecimal basePrice;
        if (request.isHoliday() && rule.getHolidayPrice() != null) {
            // Nếu là holiday và có giá holiday → dùng giá holiday cho tất cả các ngày
            long rentalDays = Math.max(1, ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()));
            basePrice = rule.getHolidayPrice().multiply(BigDecimal.valueOf(rentalDays));
        } else {
            // Tính giá theo từng ngày (tự động detect weekend)
            basePrice = pricingRuleService.calculateRentalPrice(rule, start.toLocalDate(), end.toLocalDate());
        }

        System.out.println("💰 [createOrder] Base price (trước coupon): " + basePrice);
        System.out.println("💰 [createOrder] Coupon code: " + (request.getCouponCode() != null ? request.getCouponCode() : "null"));
        System.out.println("💰 [createOrder] Coupon object: " + (coupon != null ? coupon.getCode() + " (discount: " + coupon.getDiscount() + ")" : "null"));

        BigDecimal totalPrice = couponService.applyCouponIfValid(coupon, basePrice);
        
        System.out.println("💰 [createOrder] Total price (sau coupon): " + totalPrice);

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

        // ====== CẬP NHẬT STATUS XE ======
        // Chỉ set BOOKED nếu xe đang AVAILABLE và chưa có đơn đặt nào
        // Nếu xe đang CHECKING hoặc trạng thái khác, giữ nguyên status
        String currentVehicleStatus = vehicle.getStatus();
        
        if ("AVAILABLE".equals(currentVehicleStatus)) {
            List<VehicleTimeline> existingBookings = vehicleTimelineRepository.findByVehicle_VehicleId(vehicle.getVehicleId())
                    .stream()
                    .filter(t -> "BOOKED".equalsIgnoreCase(t.getStatus()) || "RENTAL".equalsIgnoreCase(t.getStatus()))
                    .toList();

            if (existingBookings.size() == 1) {
                // Đây là lần đầu tiên đặt xe (chỉ có timeline vừa tạo) → set BOOKED
                vehicle.setStatus("BOOKED");
                vehicleRepository.save(vehicle);
                System.out.println(" [createOrder] Lần đầu tiên đặt xe " + vehicle.getVehicleId() + " → Set status = BOOKED");
            } else {
                System.out.println(" [createOrder] Xe " + vehicle.getVehicleId() + " đã có " + (existingBookings.size() - 1) + " đơn đặt, giữ nguyên status");
            }
        } else {
            // Xe đang ở trạng thái khác (CHECKING, RENTAL, BOOKED) → giữ nguyên status
            System.out.println(" [createOrder] Xe " + vehicle.getVehicleId() + " đang ở trạng thái " + currentVehicleStatus + ", giữ nguyên status");
        }

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
        rentalOrderRepository.save(order);
        return mapToResponse(order, getMainDetail(order));
    }

    @Override
    @Transactional
    public OrderResponse changeVehicle(UUID orderId, Long newVehicleId, String note) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        Vehicle newVehicle = vehicleRepository.findById(newVehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe mới"));

        // Cho phép thay đổi sang xe khác dù xe đó đang RENTAL, chỉ kiểm tra overlap thôi
        // if (!"AVAILABLE".equalsIgnoreCase(newVehicle.getStatus())) {
        //     throw new BadRequestException("Xe mới không khả dụng để thay thế");
        // }

        RentalOrderDetail mainDetail = order.getDetails().stream()
                .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Không tìm thấy chi tiết thuê"));

        // Kiểm tra xe mới có bị trùng lịch không
        if (hasOverlappingActiveBooking(newVehicle.getVehicleId(), mainDetail.getStartTime(), mainDetail.getEndTime())) {
            throw new BadRequestException("Xe mới đã được đặt trong khoảng thời gian này...");
        }

        System.out.println("[changeVehicle] Có thể thay đổi từ xe " + mainDetail.getVehicle().getVehicleId() +
                         " sang xe " + newVehicle.getVehicleId());

        Vehicle oldVehicle = mainDetail.getVehicle();
        Long oldVehicleId = oldVehicle.getVehicleId();

        // Xóa timeline của xe cũ
        deleteTimelineForOrder(orderId, oldVehicleId);

        // Giải phóng xe cũ - cập nhật status dựa vào timeline
        updateVehicleStatusFromTimeline(oldVehicleId);

        // Gán xe mới
        mainDetail.setVehicle(newVehicle);
        mainDetail.setStatus("SWITCHED");
        if (note != null && !note.isBlank()) {
            mainDetail.setDescription(note);
        }
        rentalOrderDetailRepository.save(mainDetail);

        // ====== TẠO TIMELINE MỚI ======
        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(newVehicle)
                .order(order)
                .detail(mainDetail)
                .day(mainDetail.getStartTime().toLocalDate())
                .startTime(mainDetail.getStartTime())
                .endTime(mainDetail.getEndTime())
                .status("BOOKED")
                .sourceType("VEHICLE_CHANGED")
                .note("Xe được đổi thay thế cho đơn thuê #" + order.getOrderId() +
                        (note != null ? " - " + note : ""))
                .updatedAt(LocalDateTime.now())
                .build();
        vehicleTimelineRepository.save(timeline);

        // ====== CẬP NHẬT STATUS XE MỚI DỰA VÀO TIMELINE ======
        updateVehicleStatusFromTimeline(newVehicle.getVehicleId());

        rentalOrderRepository.save(order);
        return mapToResponse(order, mainDetail);
    }

    @Override
    @Transactional
    public OrderResponse completeOrder(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        String currentStatus = order.getStatus();
        if ("COMPLETED".equals(currentStatus)) {
            throw new BadRequestException("Đơn hàng đã hoàn thành rồi");
        }

        if ("FAILED".equals(currentStatus) || "REFUNDED".equals(currentStatus)) {
            throw new BadRequestException("Không thể hoàn tất đơn hàng đã hủy hoặc đã hoàn tiền");
        }

        // Chỉ cho phép complete từ AWAITING, PAID, PENDING_FINAL_PAYMENT, hoặc RETURNED
        // (đã thanh toán hết và đã trả xe hoặc đã thanh toán đặt cọc và đang chờ nhận xe)
        boolean canComplete = "AWAITING".equals(currentStatus) ||
                             "PAID".equals(currentStatus) ||
                             "PENDING_FINAL_PAYMENT".equals(currentStatus) ||
                             "RETURNED".equals(currentStatus);
        
        if (!canComplete) {
            throw new BadRequestException("Không thể hoàn tất đơn hàng với trạng thái: " + currentStatus + 
                    ". Chỉ có thể hoàn tất đơn hàng đã thanh toán hết (AWAITING, PAID, PENDING_FINAL_PAYMENT, RETURNED)");
        }

        // Kiểm tra xem đã thanh toán hết chưa
        BigDecimal remainingAmount = calculateRemainingAmount(order);
        
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Không thể hoàn tất đơn hàng. Còn " + remainingAmount + " VND chưa thanh toán");
        }

        // Đã thanh toán hết → chuyển sang COMPLETED
        order.setStatus("COMPLETED");
        rentalOrderRepository.save(order);

        System.out.println("✅ [completeOrder] Đã hoàn tất đơn hàng " + orderId + " từ status: " + currentStatus);

        // Lấy main detail để map response
        RentalOrderDetail mainDetail = getMainDetail(order);
        return mapToResponse(order, mainDetail);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, String cancellationReason) {
        // Tìm đơn hàng
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        // Kiểm tra đơn hàng đã hoàn thành hoặc đã hủy chưa
        String currentStatus = order.getStatus();
        if (currentStatus != null) {
            String upperStatus = currentStatus.toUpperCase();
            if (upperStatus.equals("COMPLETED") || upperStatus.equals("FAILED")) {
                throw new BadRequestException("Không thể hủy đơn hàng đã hoàn thành hoặc đã hủy");
            }
        }

        // Lấy chi tiết chính
        RentalOrderDetail mainDetail = getMainDetail(order);
        if (mainDetail == null) {
            throw new BadRequestException("Không tìm thấy chi tiết đơn thuê");
        }

        // Cập nhật TẤT CẢ các detail của order thành FAILED
        List<RentalOrderDetail> allDetails = order.getDetails();
        if (allDetails != null && !allDetails.isEmpty()) {
            for (RentalOrderDetail detail : allDetails) {
                detail.setStatus("FAILED");
            }
            rentalOrderDetailRepository.saveAll(allDetails);
        }
        
        // Cập nhật status của order thành FAILED
        order.setStatus("FAILED");
        rentalOrderRepository.save(order);

        // Giải phóng xe - cập nhật status dựa vào timeline
        Vehicle vehicle = mainDetail.getVehicle();
        if (vehicle != null) {
            // Xóa timeline của đơn hàng đã hủy
            deleteTimelineForOrder(orderId, vehicle.getVehicleId());
            
            // Cập nhật status dựa vào timeline còn lại
            updateVehicleStatusFromTimeline(vehicle.getVehicleId());
        }

        // Gửi thông báo cho khách hàng
        User customer = order.getCustomer();
        if (customer != null) {
            String message = "Đơn hàng #" + orderId + " đã bị hủy";
            if (cancellationReason != null && !cancellationReason.trim().isEmpty()) {
                message += ". Lý do: " + cancellationReason;
            }
            
            Notification notification = Notification.builder()
                    .user(customer)
                    .message(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
        }

        return mapToResponse(order, mainDetail);
    }

    @Override
    @Transactional
    public void deleteOrder(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        String currentStatus = order.getStatus();
        
        // Chỉ cho phép xóa khi đơn hàng đã bị hủy hoặc hoàn thành
        List<String> allowedStatuses = List.of(
            "FAILED", // Đơn đã hủy (từ cancelOrder)
            "PAYMENT_FAILED",
            "COMPLETED", "AWAITING" // AWAITING = đã thanh toán đặt cọc, chờ nhận xe
        );
        
        boolean canDelete = allowedStatuses.stream()
                .anyMatch(status -> status.equalsIgnoreCase(currentStatus));
        
        if (!canDelete) {
            throw new BadRequestException(
                "Không thể xóa đơn hàng với trạng thái: " + currentStatus + 
                ". Chỉ có thể xóa đơn hàng đã bị hủy hoặc hoàn thành."
            );
        }

        System.out.println("[deleteOrder] Xóa đơn hàng " + orderId + " với status: " + currentStatus);

        // Lấy chi tiết chính
        RentalOrderDetail mainDetail = getMainDetail(order);

        // Nếu có detail và vehicle thì giải phóng xe
        if (mainDetail != null) {
            Vehicle vehicle = mainDetail.getVehicle();
            if (vehicle != null) {
                Long vehicleId = vehicle.getVehicleId();

                // Xóa timeline của order này
                deleteTimelineForOrder(orderId, vehicleId);

                // Cập nhật status dựa vào timeline còn lại
                System.out.println("[deleteOrder] Đơn " + orderId + " bị xóa, cập nhật status xe " + vehicleId + " dựa vào timeline");
                updateVehicleStatusFromTimeline(vehicleId);
            }
        }

        // Xóa order (cascade sẽ xóa các bản ghi liên quan nếu có)
        rentalOrderRepository.delete(order);
        System.out.println("[deleteOrder] Đã xóa đơn hàng " + orderId + " thành công");
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

                        if (v != null) {
                            res.setPlateNumber(v.getPlateNumber());
                            if (v.getRentalStation() != null) {
                                res.setStationId(v.getRentalStation().getStationId());
                                res.setStationName(v.getRentalStation().getName());
                            }
                            
                            // Lấy thông tin từ VehicleModel
                            VehicleModel model = vehicleModelService.findByVehicle(v);
                            if (model != null) {
                                res.setBrand(model.getBrand());
                                res.setCarmodel(model.getCarmodel());
                            }
                        }
                    }

                    res.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
                    res.setTotalPrice(order.getTotalPrice());
                    res.setStatus(order.getStatus());
                    
                    // Lấy số tiền còn lại chưa thanh toán từ Payment
                    BigDecimal remainingAmount = calculateRemainingAmount(order);
                    res.setRemainingAmount(remainingAmount);

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

        String currentStatus = order.getStatus();
        if (!"AWAITING".equals(currentStatus) && !"PAID".equals(currentStatus)) {
            throw new BadRequestException("Chỉ có thể bàn giao xe khi đơn hàng ở trạng thái AWAITING (đã thanh toán đặt cọc) hoặc PAID (đã thanh toán hết dịch vụ)");
        }

        //  Lấy chi tiết chính (RENTAL)
        RentalOrderDetail mainDetail = getMainDetail(order);
        if (mainDetail == null)
            throw new BadRequestException("Không tìm thấy chi tiết đơn thuê chính (RENTAL)");

        //  Lấy xe
        Vehicle vehicle = mainDetail.getVehicle();
        if (vehicle == null)
            throw new BadRequestException("Không tìm thấy xe trong chi tiết đơn");

        // Lưu batteryStatus ban đầu vào description của detail PICKUP để dùng khi trả xe
        // (Battery status được lưu trong VehicleModel, không cần thêm vào description)
        VehicleModel vehicleModel = vehicleModelService.findByVehicle(vehicle);
        if (vehicleModel != null && vehicleModel.getBatteryStatus() != null) {
            String initialBattery = vehicleModel.getBatteryStatus();
            System.out.println("🔋 [confirmPickup] BatteryStatus ban đầu: " + initialBattery);
        }

        // Kiểm tra xe không đang được người khác thuê
        // Đây là check quan trọng: nếu có khách hàng khác đã nhận xe (order status = RENTAL),
        // thì khách hàng này không thể nhận xe cho đến khi xe được trả về
        // Logic: Tìm tất cả đơn có status RENTAL của xe này (không phải đơn hiện tại)
        List<RentalOrder> rentalOrders = rentalOrderRepository.findByStatus("RENTAL");
        boolean isRentedByOther = rentalOrders.stream()
                .filter(o -> !o.getOrderId().equals(orderId))
                .anyMatch(o -> o.getDetails().stream()
                        .anyMatch(d -> "RENTAL".equalsIgnoreCase(d.getType())
                                && d.getVehicle() != null
                                && d.getVehicle().getVehicleId().equals(vehicle.getVehicleId())));

        if (isRentedByOther) {
            throw new ConflictException("Xe đang được khách hàng khác thuê. Không thể bàn giao xe! Vui lòng đợi đến khi xe được trả về.");
        }

        //  Cập nhật trạng thái — KHÔNG tạo thêm detail nào
        order.setStatus("RENTAL");
        vehicle.setStatus("RENTAL");

        //  Lưu DB
        rentalOrderDetailRepository.save(mainDetail);
        vehicleRepository.save(vehicle);
        rentalOrderRepository.save(order);

        //  XÓA TIMELINE BOOKED của đơn này (nếu có) vì đã chuyển sang RENTAL
        List<VehicleTimeline> bookedTimelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicle.getVehicleId())
                .stream()
                .filter(t -> t.getOrder() != null && t.getOrder().getOrderId().equals(orderId))
                .filter(t -> "BOOKED".equalsIgnoreCase(t.getStatus()))
                .toList();
        
        if (!bookedTimelines.isEmpty()) {
            vehicleTimelineRepository.deleteAll(bookedTimelines);
            System.out.println("[confirmPickup] Đã xóa " + bookedTimelines.size() + " timeline BOOKED của đơn " + orderId);
        }

        //  Tạo timeline RENTAL mới
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

        // THÔNG BÁO CHO CÁC KHÁCH HÀNG KHÁC ĐÃ BOOK CÙNG XE
        notifyOtherCustomersAndUpdateStatus(vehicle.getVehicleId(), orderId, vehicle.getPlateNumber());

        // Tăng pickup_count cho staff hiện tại (nếu có)
        UUID staffId = getCurrentStaffId();
        System.out.println("[confirmPickup] staffId from JWT: " + staffId);
        if (staffId != null) {
            System.out.println("[confirmPickup] Calling incrementPickupCount...");
            incrementPickupCount(staffId);
        } else {
            System.out.println("[confirmPickup] staffId is null, skip incrementPickupCount");
        }

        return mapToResponse(order, mainDetail);
    }

    @Override
    @Transactional
    public OrderResponse confirmReturn(UUID orderId, OrderReturnRequest request) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        RentalOrderDetail mainDetail = getMainDetail(order);
        Vehicle vehicle = mainDetail.getVehicle();
        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleByCarmodel(model.getCarmodel());

        // Lấy actualReturnTime từ request, nếu null thì dùng endTime từ detail
        LocalDateTime actualReturnTime;
        if (request != null && request.getActualReturnTime() != null) {
            actualReturnTime = request.getActualReturnTime();
        } else {
            // Nếu không nhập thì lấy thời gian kết thúc dự kiến từ detail
            actualReturnTime = mainDetail.getEndTime();
        }

        // Tính số ngày thuê thực tế và số ngày dự kiến
        long actualDays = ChronoUnit.DAYS.between(mainDetail.getStartTime(), actualReturnTime);
        long expectedDays = ChronoUnit.DAYS.between(mainDetail.getStartTime(), mainDetail.getEndTime());

        // Tính phí trễ nếu trả muộn và cộng vào totalPrice
        BigDecimal currentTotal = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
        if (actualDays > expectedDays) {
            long lateDays = actualDays - expectedDays;
            BigDecimal lateFeePerDay = rule.getLateFeePerDay() != null ? rule.getLateFeePerDay() : BigDecimal.ZERO;
            BigDecimal lateFee = lateFeePerDay.multiply(BigDecimal.valueOf(lateDays));
            
            System.out.println("💰 [confirmReturn] Khách trả xe trễ " + lateDays + " ngày, phí trễ: " + lateFee);
            System.out.println("💰 [confirmReturn] TotalPrice trước: " + currentTotal);
            
            // Cộng phí trễ vào totalPrice
            currentTotal = currentTotal.add(lateFee);
            order.setTotalPrice(currentTotal);
            
            System.out.println("💰 [confirmReturn] TotalPrice sau khi cộng phí trễ: " + currentTotal);
            
            // Tạo detail cho phí trễ (nếu có)
            if (lateFee.compareTo(BigDecimal.ZERO) > 0) {
                RentalOrderDetail lateFeeDetail = RentalOrderDetail.builder()
                        .order(order)
                        .vehicle(vehicle)
                        .type("SERVICE")
                        .startTime(mainDetail.getEndTime())
                        .endTime(actualReturnTime)
                        .price(lateFee)
                        .status("PENDING")
                        .description("Phí trễ hạn " + lateDays + " ngày")
                        .build();
                rentalOrderDetailRepository.save(lateFeeDetail);
                System.out.println("✅ [confirmReturn] Đã tạo detail phí trễ: " + lateFee);
                
                // Cập nhật remainingAmount của payment nếu có (giống logic createService)
                List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(order.getOrderId());
                
                // Tìm payment type 1 (deposit) SUCCESS
                Optional<Payment> depositPayment = payments.stream()
                        .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                        .findFirst();
                
                if (depositPayment.isPresent()) {
                    Payment deposit = depositPayment.get();
                    BigDecimal currentRemaining = deposit.getRemainingAmount() != null 
                            ? deposit.getRemainingAmount() 
                            : BigDecimal.ZERO;
                    deposit.setRemainingAmount(currentRemaining.add(lateFee));
                    paymentRepository.save(deposit);
                    System.out.println("✅ [confirmReturn] Đã cập nhật remainingAmount cho deposit payment: " + 
                            currentRemaining + " + " + lateFee + " = " + deposit.getRemainingAmount());
                } else {
                    // Tìm payment type 3 (full payment) SUCCESS
                    Optional<Payment> fullPayment = payments.stream()
                            .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                            .findFirst();
                    
                    if (fullPayment.isPresent()) {
                        Payment full = fullPayment.get();
                        // Type 3 đã thanh toán hết, giờ cần thanh toán thêm phí trễ
                        BigDecimal currentRemaining = full.getRemainingAmount() != null 
                                ? full.getRemainingAmount() 
                                : BigDecimal.ZERO;
                        full.setRemainingAmount(currentRemaining.add(lateFee));
                        paymentRepository.save(full);
                        System.out.println("✅ [confirmReturn] Đã cập nhật remainingAmount cho full payment: " + 
                                currentRemaining + " + " + lateFee + " = " + full.getRemainingAmount());
                    }
                }
            }
        } else if (actualDays < expectedDays) {
            System.out.println("ℹ️ [confirmReturn] Khách trả xe sớm " + (expectedDays - actualDays) + " ngày");
        }

        // Tự động giả lập pin khi trả xe
        VehicleModel vehicleModel = vehicleModelService.findByVehicle(vehicle);
        if (vehicleModel != null) {
            // Lấy batteryStatus ban đầu từ VehicleModel (không cần tìm trong description nữa)
            String initialBatteryStr = null;
            if (vehicleModel.getBatteryStatus() != null) {
                initialBatteryStr = vehicleModel.getBatteryStatus().replace("%", "").trim();
            }
            
            if (initialBatteryStr != null) {
                try {
                    int initialBattery = Integer.parseInt(initialBatteryStr);
                    // Giả lập pin: random từ max(20, initialBattery - 60) đến initialBattery
                    // Đảm bảo không dưới 20% và không quá giá trị ban đầu
                    int minBattery = Math.max(20, initialBattery - 60);
                    int maxBattery = initialBattery;
                    
                    // Random pin trong khoảng minBattery đến maxBattery
                    Random random = new Random();
                    int newBattery = random.nextInt(maxBattery - minBattery + 1) + minBattery;
                    
                    vehicleModel.setBatteryStatus(newBattery + "%");
                    vehicleModelRepository.save(vehicleModel);
                    System.out.println("🔋 [confirmReturn] Pin ban đầu: " + initialBattery + "%, Pin sau khi trả: " + newBattery + "%");
                } catch (NumberFormatException e) {
                    System.out.println("⚠️ [confirmReturn] Không thể parse batteryStatus: " + initialBatteryStr);
                }
            }
        }

        // Reload order từ DB để đảm bảo có dữ liệu mới nhất (payments, totalPrice, etc.)
        order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));
        
        // Kiểm tra xem còn số tiền chưa thanh toán không (phí trễ hoặc dịch vụ)
        BigDecimal remainingAmount = calculateRemainingAmount(order);
        
        System.out.println("💰 [confirmReturn] RemainingAmount sau khi tính: " + remainingAmount);
        System.out.println("💰 [confirmReturn] Order status hiện tại: " + order.getStatus());
        
        // Set vehicle status
        vehicle.setStatus("CHECKING");
        
        // Nếu có phí trễ hoặc dịch vụ chưa thanh toán → chuyển thành PENDING_FINAL_PAYMENT
        // KHÔNG tự động set COMPLETED khi đã thanh toán hết
        // Chỉ khi gọi API /complete thì mới set COMPLETED
        if (remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
            // Đã thanh toán hết → chuyển thành PENDING_FINAL_PAYMENT (chờ gọi API /complete)
            order.setStatus("PENDING_FINAL_PAYMENT");
            System.out.println("✅ [confirmReturn] Đã thanh toán hết → chuyển thành PENDING_FINAL_PAYMENT (chờ gọi API /complete)");
        } else {
            // Có phí trễ hoặc dịch vụ chưa thanh toán → chuyển thành PENDING_FINAL_PAYMENT
            order.setStatus("PENDING_FINAL_PAYMENT");
            System.out.println("ℹ️ [confirmReturn] Còn " + remainingAmount + " chưa thanh toán (phí trễ/dịch vụ) → chuyển thành PENDING_FINAL_PAYMENT");
        }

        // Xóa timeline khi order hoàn thành (xe đã trả, không cần track nữa)
        deleteTimelineForOrder(orderId, vehicle.getVehicleId());

        // KIỂM TRA XE AVAILABLE: Nếu xe available, kiểm tra có timeline đầu tiên thì chuyển sang BOOKED
        checkAndTransitionToNextBooking(vehicle.getVehicleId());

        vehicleRepository.save(vehicle);
        // GIỮ NGUYÊN order.totalPrice - không thay đổi giá đã thanh toán
        rentalOrderRepository.save(order);

        // Tăng return_count cho staff hiện tại (nếu có)
        UUID staffId = getCurrentStaffId();
        System.out.println("[confirmReturn] staffId from JWT: " + staffId);
        if (staffId != null) {
            System.out.println("[confirmReturn] Calling incrementReturnCount...");
            incrementReturnCount(staffId);
        } else {
            System.out.println("[confirmReturn] staffId is null, skip incrementReturnCount");
        }

        return mapToResponse(order, mainDetail);
    }

    @Override
    public OrderResponse previewReturn(UUID orderId, Integer actualDays) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        RentalOrderDetail mainDetail = getMainDetail(order);
        Vehicle vehicle = mainDetail.getVehicle();
        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        PricingRule rule = pricingRuleService.getPricingRuleByCarmodel(model.getCarmodel());

        long actualDaysCount = actualDays != null
                ? actualDays
                : ChronoUnit.DAYS.between(mainDetail.getStartTime(), LocalDateTime.now());

        long expectedDays = ChronoUnit.DAYS.between(mainDetail.getStartTime(), mainDetail.getEndTime());

        // Bắt đầu với giá đã thanh toán
        BigDecimal total = order.getTotalPrice();

        // Chỉ cộng thêm phí trễ nếu trả muộn
        if (actualDaysCount > expectedDays) {
            long lateDays = actualDaysCount - expectedDays;
            BigDecimal lateFee = rule.getLateFeePerDay().multiply(BigDecimal.valueOf(lateDays));
            total = total.add(lateFee);
        }

        //  KHÔNG cập nhật order, chỉ tạo response
        OrderResponse response = mapToResponse(order, mainDetail);
        response.setTotalPrice(total);
        response.setStatus(order.getStatus()); // Giữ nguyên trạng thái hiện tại
        return response;
    }

    @Override
    public List<OrderVerificationResponse> getPendingVerificationOrders() {
        // Lấy tất cả đơn chưa hoàn tất
        List<RentalOrder> processingOrders = rentalOrderRepository.findAll().stream()
                .filter(o -> {
                    String s = Optional.ofNullable(o.getStatus()).orElse("").toUpperCase();
                    return s.startsWith("PENDING")
                            || s.equals("COMPLETED")
                            || s.equals("AWAITING")             // đã thanh toán đặt cọc, chờ nhận xe
                            || s.equals("PAID")                 // đã thanh toán hết dịch vụ
                            || s.equals("RENTAL")               // đang thuê
                            || s.equals("DEPOSITED")
                            || s.equals("SERVICE_PAID")         // đã đặt cọc
                            || s.equals("PENDING_FINAL_PAYMENT"); // chờ thanh toán cuối (services + phí trễ)
                })
                //  sort theo createdAt mới nhất
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();

        return processingOrders.stream().map(order -> {
            User customer = order.getCustomer();

            // Lấy chi tiết chính
            RentalOrderDetail rentalDetail = Optional.ofNullable(order.getDetails())
                    .orElse(List.of()).stream()
                    .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                    .findFirst()
                    .orElse(null);

            Vehicle vehicle = rentalDetail != null ? rentalDetail.getVehicle() : null;
            RentalStation station = vehicle != null ? vehicle.getRentalStation() : null;

            // Tổng phí dịch vụ phát sinh
            BigDecimal totalServiceCost = BigDecimal.ZERO;

            // Tổng tiền = order.totalPrice (giá thuê)
            BigDecimal totalPrice = Optional.ofNullable(order.getTotalPrice()).orElse(BigDecimal.ZERO);

            // Lấy số tiền còn lại chưa thanh toán từ Payment
            BigDecimal remainingAmount = calculateRemainingAmount(order);
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

                    .totalPrice(totalPrice)
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

    @Override
    public List<OrderDetailCompactResponse> getCompactDetailsByVehicle(Long vehicleId) {

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe"));

        return rentalOrderRepository.findOrdersByVehicleId(vehicleId)
                .stream()
                .map(order -> {

                    OrderDetailCompactResponse dto = new OrderDetailCompactResponse();

                    dto.setOrderId(order.getOrderId());
                    dto.setPrice(order.getTotalPrice());
                    dto.setStatus(order.getStatus());
                    dto.setCreatedAt(order.getCreatedAt());

                    // customer
                    User customer = order.getCustomer();
                    dto.setCustomerName(customer.getFullName());
                    dto.setCustomerPhone(customer.getPhone());

                    // station
                    if (vehicle.getRentalStation() != null) {
                        dto.setStationName(vehicle.getRentalStation().getName());
                    }

                    return dto;
                })
                .toList();
    }

    @Override
    public OrderDetailCompactResponse updateCompactOrder(Long vehicleId, UUID orderId, CompactOrderUpdateRequest req) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Ensure vehicle match
        RentalOrderDetail detail = rentalOrderDetailRepository
                .findByOrder_OrderId(orderId)
                .stream()
                .filter(d -> d.getVehicle().getVehicleId().equals(vehicleId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Order does not belong to this vehicle"));

        // Update fields
        if (req.getStatus() != null) {
            order.setStatus(req.getStatus());
        }

        if (req.getPrice() != null) {
            detail.setPrice(req.getPrice());
        }

        if (req.getStationName() != null) {
            Vehicle v = detail.getVehicle();
            if (v.getRentalStation() != null) {
                v.getRentalStation().setName(req.getStationName());
            }
        }

        rentalOrderRepository.save(order);
        rentalOrderDetailRepository.save(detail);

        // Return updated compact
        OrderDetailCompactResponse res = new OrderDetailCompactResponse();
        res.setOrderId(orderId);
        res.setPrice(detail.getPrice());
        res.setStatus(order.getStatus());
        res.setCreatedAt(order.getCreatedAt());
        res.setCustomerName(order.getCustomer().getFullName());
        res.setCustomerPhone(order.getCustomer().getPhone());
        res.setStationName(detail.getVehicle().getRentalStation().getName());

        return res;
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

    /**
     * Tính số tiền còn lại chưa thanh toán
     * Logic mới:
     * - DEPOSIT: remainingAmount = total - deposit (bao gồm cả dịch vụ được thêm vào sau)
     * - FULL_PAYMENT: remainingAmount = 0 ban đầu, cộng thêm dịch vụ khi thêm
     * - PICKUP: Dựa vào remainingAmount của DEPOSIT hoặc FULL_PAYMENT
     * - KHÔNG cộng SERVICE PENDING vì đã có trong remainingAmount của payment rồi
     */
    private BigDecimal calculateRemainingAmount(RentalOrder order) {
        // Fetch payments từ repository để đảm bảo load đầy đủ (tránh lazy loading issue)
        List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(order.getOrderId());
        
        if (payments == null || payments.isEmpty()) {
            // Chưa thanh toán gì → trả về totalPrice
            BigDecimal totalPrice = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
            System.out.println("⚠️ [calculateRemainingAmount] Không có payment SUCCESS nào → remainingAmount = " + totalPrice);
            return totalPrice;
        }
        
        // Kiểm tra FULL_PAYMENT (type 3) SUCCESS
        Optional<Payment> fullPayment = payments.stream()
                .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();
        
        if (fullPayment.isPresent()) {
            // FULL_PAYMENT: remainingAmount đã bao gồm cả dịch vụ (ban đầu = 0, cộng thêm khi thêm dịch vụ)
            BigDecimal remaining = fullPayment.get().getRemainingAmount();
            BigDecimal result = remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 
                    ? remaining 
                    : BigDecimal.ZERO;
            System.out.println("✅ [calculateRemainingAmount] FULL_PAYMENT SUCCESS → remainingAmount = " + result);
            return result;
        }
        
        // Kiểm tra FINAL_PAYMENT (type 2) SUCCESS → đã thanh toán hết phần còn lại của DEPOSIT
        // Nhưng có thể còn remainingAmount của DEPOSIT (dịch vụ mới thêm)
        boolean hasFinalPaymentSuccess = payments.stream()
                .anyMatch(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.SUCCESS);
        if (hasFinalPaymentSuccess) {
            // Đã thanh toán PICKUP, kiểm tra xem DEPOSIT còn remainingAmount không (dịch vụ mới)
            Optional<Payment> depositPayment = payments.stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();
            
            if (depositPayment.isPresent()) {
                BigDecimal remaining = depositPayment.get().getRemainingAmount();
                BigDecimal result = remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 
                        ? remaining 
                        : BigDecimal.ZERO;
                System.out.println("✅ [calculateRemainingAmount] FINAL_PAYMENT SUCCESS, còn remainingAmount từ DEPOSIT = " + result);
                return result;
            }
            // Đã thanh toán hết
            System.out.println("✅ [calculateRemainingAmount] FINAL_PAYMENT SUCCESS → remainingAmount = 0");
            return BigDecimal.ZERO;
        }
        
        // Kiểm tra DEPOSIT (type 1) SUCCESS → lấy remainingAmount (đã bao gồm dịch vụ)
        Optional<Payment> depositPayment = payments.stream()
                .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();
        
        if (depositPayment.isPresent()) {
            // DEPOSIT: remainingAmount = phần còn lại từ đặt cọc + dịch vụ đã thêm
            BigDecimal remaining = depositPayment.get().getRemainingAmount();
            BigDecimal result = remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 
                    ? remaining 
                    : BigDecimal.ZERO;
            System.out.println("💰 [calculateRemainingAmount] DEPOSIT SUCCESS → remainingAmount = " + result);
            return result;
        }
        
        // Chưa thanh toán gì → trả về totalPrice
        BigDecimal totalPrice = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
        System.out.println("⚠️ [calculateRemainingAmount] Không có payment SUCCESS nào → remainingAmount = " + totalPrice);
        return totalPrice;
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

        // Lấy số tiền còn lại chưa thanh toán từ Payment
        BigDecimal remainingAmount = calculateRemainingAmount(order);
        res.setRemainingAmount(remainingAmount);

        if (v != null) {
            res.setPlateNumber(v.getPlateNumber());
            if (v.getRentalStation() != null) {
                res.setStationId(v.getRentalStation().getStationId());
                res.setStationName(v.getRentalStation().getName());
            }
            
            // Lấy thông tin từ VehicleModel
            VehicleModel model = vehicleModelService.findByVehicle(v);
            if (model != null) {
                res.setBrand(model.getBrand());
                res.setCarmodel(model.getCarmodel());
            }
        }

        return res;
    }

    private JwtUserDetails currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserDetails jwt))
            throw new BadRequestException("Phiên đăng nhập không hợp lệ");
        return jwt;
    }

    /**
     * Lấy userId của staff hiện tại từ JWT token (nếu có)
     * Return null nếu không có authentication
     */
    private UUID getCurrentStaffId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof JwtUserDetails jwt) {
                return jwt.getUserId();
            }
        } catch (Exception e) {
            System.err.println("Không thể lấy userId từ JWT: " + e.getMessage());
        }
        return null;
    }


    private String getCurrentShiftTime() {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 6 && hour < 12) {
            return "MORNING";
        } else if (hour >= 12 && hour < 18) {
            return "AFTERNOON";
        } else if (hour >= 18 && hour < 22) {
            return "EVENING";
        }
        return "NIGHT"; // 22-6
    }

    /**
     * Tăng pickup_count cho staff trong ca làm việc hiện tại
     */
    private void incrementPickupCount(UUID staffId) {
        try {
            String shiftTime = getCurrentShiftTime();
            java.time.LocalDate today = java.time.LocalDate.now();

            Optional<EmployeeSchedule> scheduleOpt =
                    employeeScheduleRepository.findByStaff_UserIdAndShiftDateAndShiftTime(
                            staffId, today, shiftTime);

            if (scheduleOpt.isPresent()) {
                EmployeeSchedule schedule = scheduleOpt.get();
                int oldCount = schedule.getPickupCount();
                schedule.setPickupCount(oldCount + 1);
                employeeScheduleRepository.save(schedule);
                System.out.println("Đã cập nhật pickup_count: " + oldCount + " → " + (oldCount + 1) +
                        " cho staff " + staffId + " vào ca " + shiftTime);
            } else {
                // Nếu không tìm thấy schedule, tự động tạo mới
                System.out.println("Không tìm thấy schedule cho staff " + staffId +
                        " vào ngày " + today + " ca " + shiftTime);

                // Lấy thông tin staff để lấy station
                User staff = userRepository.findById(staffId).orElse(null);
                if (staff != null && staff.getRentalStation() != null) {
                    EmployeeSchedule newSchedule = EmployeeSchedule.builder()
                            .staff(staff)
                            .station(staff.getRentalStation())
                            .shiftDate(today)
                            .shiftTime(shiftTime)
                            .pickupCount(1)
                            .returnCount(0)
                            .build();
                    employeeScheduleRepository.save(newSchedule);
                    System.out.println("Đã tự động tạo schedule mới và cập nhật pickup_count = 1");
                } else {
                    System.err.println("Không thể tạo schedule: Staff không có station");
                }
            }
        } catch (Exception e) {
            // Log error nhưng không throw exception để không ảnh hưởng flow chính
            System.err.println("Failed to increment pickup count: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Tăng return_count cho staff trong ca làm việc hiện tại
     */
    private void incrementReturnCount(UUID staffId) {
        System.out.println("[incrementReturnCount] START - staffId: " + staffId);
        try {
            String shiftTime = getCurrentShiftTime();
            java.time.LocalDate today = java.time.LocalDate.now();
            System.out.println("[incrementReturnCount] Shift: " + shiftTime + ", Date: " + today);

            Optional<EmployeeSchedule> scheduleOpt =
                    employeeScheduleRepository.findByStaff_UserIdAndShiftDateAndShiftTime(
                            staffId, today, shiftTime);

            if (scheduleOpt.isPresent()) {
                EmployeeSchedule schedule = scheduleOpt.get();
                int oldCount = schedule.getReturnCount();
                schedule.setReturnCount(oldCount + 1);
                employeeScheduleRepository.save(schedule);
                System.out.println("Đã cập nhật return_count: " + oldCount + " → " + (oldCount + 1) +
                        " cho staff " + staffId + " vào ca " + shiftTime);
            } else {
                // Nếu không tìm thấy schedule, tự động tạo mới
                System.out.println("Không tìm thấy schedule cho staff " + staffId +
                        " vào ngày " + today + " ca " + shiftTime);

                // Lấy thông tin staff để lấy station
                User staff = userRepository.findById(staffId).orElse(null);
                if (staff != null && staff.getRentalStation() != null) {
                    EmployeeSchedule newSchedule = EmployeeSchedule.builder()
                            .staff(staff)
                            .station(staff.getRentalStation())
                            .shiftDate(today)
                            .shiftTime(shiftTime)
                            .pickupCount(0)
                            .returnCount(1)
                            .build();
                    employeeScheduleRepository.save(newSchedule);
                    System.out.println("Đã tự động tạo schedule mới và cập nhật return_count = 1");
                } else {
                    System.err.println("Không thể tạo schedule: Staff không có station");
                }
            }
        } catch (Exception e) {
            // Log error nhưng không throw exception để không ảnh hưởng flow chính
            System.err.println("Failed to increment return count: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("[incrementReturnCount] END");
    }

    /**
     * Kiểm tra xem xe có booking trùng lặp trong khoảng thời gian không
     * Cho phép multiple bookings nếu thời gian không trùng nhau
     * Status: pending | confirmed | active | done | cancelled
     */
    private boolean hasOverlappingActiveBooking(Long vehicleId, LocalDateTime requestStart, LocalDateTime requestEnd) {
        System.out.println("[hasOverlappingActiveBooking] Kiểm tra xe " + vehicleId +
                         " cho thời gian: [" + requestStart + " - " + requestEnd + "]");

        // Lấy tất cả chi tiết đơn đang ACTIVE (pending, confirmed, active - không including done/cancelled)
        List<RentalOrderDetail> activeDetails = rentalOrderDetailRepository
                .findByVehicle_VehicleIdAndStatusIn(vehicleId, List.of("pending", "confirmed", "active"));

        System.out.println("Tổng booking active: " + activeDetails.size());

        for (RentalOrderDetail detail : activeDetails) {
            // Kiểm tra overlap: (start1 < end2) AND (end1 > start2)
            LocalDateTime existingStart = detail.getStartTime();
            LocalDateTime existingEnd = detail.getEndTime();

            System.out.println("  - Booking: [" + existingStart + " - " + existingEnd + "] Status: " + detail.getStatus() + " Type: " + detail.getType());

            if (existingStart != null && existingEnd != null) {
                // Nếu booking mới bắt đầu trước hoặc bằng lúc booking cũ kết thúc → OK
                // Nếu booking mới kết thúc trước hoặc bằng lúc booking cũ bắt đầu → OK
                // Nếu không thì bị overlap
                boolean overlaps = requestStart.isBefore(existingEnd) && requestEnd.isAfter(existingStart);
                if (overlaps) {
                    System.out.println("Có booking trùng lặp: [" + existingStart + " - " + existingEnd +
                                     "] với request [" + requestStart + " - " + requestEnd + "]");
                    return true; // Có overlap với booking đang active
                } else {
                    System.out.println("Không trùng lặp");
                }
            }
        }

        System.out.println("Không có booking trùng lặp cho xe " + vehicleId);
        return false; // Không có overlap
    }

    /**
     * Xóa timeline khi order hoàn thành hoặc bị hủy
     * Timeline chỉ dùng để track xe đang được book, không cần lưu lịch sử
     */
    private void deleteTimelineForOrder(UUID orderId, Long vehicleId) {
        if (vehicleId == null) return;

        List<VehicleTimeline> timelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId);
        List<VehicleTimeline> toDelete = timelines.stream()
                .filter(t -> t.getOrder() != null && t.getOrder().getOrderId().equals(orderId))
                .toList();

        if (!toDelete.isEmpty()) {
            vehicleTimelineRepository.deleteAll(toDelete);
        }
    }

    private void checkAndTransitionToNextBooking(Long vehicleId) {
        System.out.println("[checkAndTransitionToNextBooking] Kiểm tra xe " + vehicleId);

        // Bước 1: Kiểm tra trạng thái xe hiện tại
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId);
        if (vehicleOpt.isEmpty()) {
            System.out.println("Không tìm thấy xe " + vehicleId);
            return;
        }

        Vehicle vehicle = vehicleOpt.get();
        String currentStatus = vehicle.getStatus();
        System.out.println("Trạng thái xe hiện tại: " + currentStatus);

        // Bước 2: Chỉ kiểm tra và chuyển đổi NẾU xe đang AVAILABLE
        if (!"AVAILABLE".equals(currentStatus)) {
            System.out.println("Xe không ở trạng thái AVAILABLE, bỏ qua kiểm tra booking");
            return;
        }

        // Bước 3: Lấy tất cả booking pending/confirmed/WAITING của xe này (chưa active)
        // Ưu tiên WAITING trước, sau đó mới đến PENDING/CONFIRMED
        List<RentalOrderDetail> waitingBookings = rentalOrderDetailRepository
                .findByVehicle_VehicleIdAndStatusIn(vehicleId, List.of("WAITING"));
        
        List<RentalOrderDetail> pendingBookings = waitingBookings.isEmpty() 
                ? rentalOrderDetailRepository.findByVehicle_VehicleIdAndStatusIn(vehicleId, List.of("PENDING", "CONFIRMED"))
                : waitingBookings;

        if (pendingBookings.isEmpty()) {
            System.out.println("Xe AVAILABLE, không có booking tiếp theo trong hàng chờ");
            return;
        }

        // Bước 4: Lấy booking sớm nhất (theo startTime)
        // Nếu có WAITING thì ưu tiên WAITING, nếu không thì lấy PENDING/CONFIRMED sớm nhất
        RentalOrderDetail nextBooking = pendingBookings.stream()
                .min(java.util.Comparator.comparing(RentalOrderDetail::getStartTime))
                .orElse(null);

        if (nextBooking != null) {
            LocalDateTime nextStart = nextBooking.getStartTime();
            LocalDateTime nextEnd = nextBooking.getEndTime();

            System.out.println("Booking tiếp theo: [" + nextStart + " - " + nextEnd +
                             "] Status: " + nextBooking.getStatus());

            // Nếu booking có status WAITING, chuyển về CONFIRMED để khách hàng có thể nhận xe
            if ("WAITING".equalsIgnoreCase(nextBooking.getStatus())) {
                nextBooking.setStatus("CONFIRMED");
                rentalOrderDetailRepository.save(nextBooking);
                System.out.println("Đã chuyển status từ WAITING → CONFIRMED cho booking " + nextBooking.getOrder().getOrderId());
                
                // Gửi thông báo cho khách hàng rằng xe đã có sẵn
                RentalOrder waitingOrder = nextBooking.getOrder();
                if (waitingOrder != null && waitingOrder.getCustomer() != null) {
                    String message = "Xe " + (vehicle.getPlateNumber() != null ? vehicle.getPlateNumber() : "của bạn") + 
                                   " đã có sẵn. Bạn có thể đến nhận xe.";
                    Notification notification = Notification.builder()
                            .user(waitingOrder.getCustomer())
                            .message(message)
                            .createdAt(LocalDateTime.now())
                            .build();
                    notificationRepository.save(notification);
                    System.out.println("Đã gửi thông báo xe có sẵn cho khách hàng " + waitingOrder.getCustomer().getUserId());
                }
            }

            // Tạo timeline cho booking tiếp theo
            LocalDateTime now = LocalDateTime.now();
            VehicleTimeline timeline = VehicleTimeline.builder()
                    .vehicle(vehicle)
                    .order(nextBooking.getOrder())
                    .detail(nextBooking)
                    .day(nextStart.toLocalDate())
                    .startTime(nextStart)
                    .endTime(nextEnd)
                    .status("BOOKED")
                    .sourceType("AUTO_QUEUE")
                    .note("Tự động chuyển từ hàng chờ để chuẩn bị cho booking #" + nextBooking.getOrder().getOrderId())
                    .updatedAt(now)
                    .build();
            vehicleTimelineRepository.save(timeline);

            // Cập nhật status dựa vào timeline
            System.out.println("Xe " + vehicleId + " → cập nhật status dựa vào timeline sau khi tạo booking tiếp theo");
            updateVehicleStatusFromTimeline(vehicleId);
        }
    }

    /**
     * Thông báo cho các khách hàng khác đã book cùng xe và cập nhật status thành WAITING
     * Khi một khách hàng nhận xe, các khách hàng khác đã book cùng xe sẽ nhận thông báo
     */
    private void notifyOtherCustomersAndUpdateStatus(Long vehicleId, UUID currentOrderId, String plateNumber) {
        System.out.println("[notifyOtherCustomersAndUpdateStatus] Xe " + vehicleId + " đã được khách hàng nhận, tìm các booking khác...");
        
        // Tìm tất cả các booking của xe này có status PENDING hoặc CONFIRMED (không phải đơn hiện tại)
        List<RentalOrderDetail> otherBookings = rentalOrderDetailRepository
                .findByVehicle_VehicleIdAndStatusIn(vehicleId, List.of("PENDING", "CONFIRMED"))
                .stream()
                .filter(detail -> {
                    // Loại bỏ đơn hiện tại
                    return detail.getOrder() != null && !detail.getOrder().getOrderId().equals(currentOrderId);
                })
                .collect(Collectors.toList());

        System.out.println("Tìm thấy " + otherBookings.size() + " booking khác của xe " + vehicleId);

        for (RentalOrderDetail detail : otherBookings) {
            RentalOrder otherOrder = detail.getOrder();
            if (otherOrder == null || otherOrder.getCustomer() == null) {
                continue;
            }

            User otherCustomer = otherOrder.getCustomer();
            
            // Cập nhật status của detail thành WAITING (hardcoded)
            detail.setStatus("WAITING");
            rentalOrderDetailRepository.save(detail);
            System.out.println("Đã cập nhật status của order " + otherOrder.getOrderId() + " → WAITING");

            // Tạo thông báo cho khách hàng
            String message = "Xe " + (plateNumber != null ? plateNumber : "của bạn") + 
                           " đã được khách hàng khác thuê. Bạn đang trong hàng chờ và sẽ được thông báo khi xe có sẵn.";
            
            Notification notification = Notification.builder()
                    .user(otherCustomer)
                    .message(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            notificationRepository.save(notification);
            System.out.println("Đã gửi thông báo cho khách hàng " + otherCustomer.getUserId() + 
                            " (Order: " + otherOrder.getOrderId() + ")");
        }

        System.out.println("Hoàn tất thông báo cho " + otherBookings.size() + " khách hàng khác");
    }

    /**
     * Cập nhật status của xe dựa vào timeline
     * CHỈ GỌI KHI XE ĐANG Ở TRẠNG THÁI AVAILABLE
     * Logic:
     * - Nếu có timeline RENTAL đang active (thời gian hiện tại nằm trong khoảng start-end) → RENTAL
     * - Nếu không, kiểm tra có timeline BOOKED sớm nhất trong tương lai → BOOKED
     * - Nếu không có timeline nào → AVAILABLE
     */
    private void updateVehicleStatusFromTimeline(Long vehicleId) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId);
        if (vehicleOpt.isEmpty()) {
            System.out.println("[updateVehicleStatusFromTimeline] Không tìm thấy xe " + vehicleId);
            return;
        }

        Vehicle vehicle = vehicleOpt.get();
        String currentStatus = vehicle.getStatus();
        
        // CHỈ cập nhật nếu xe đang AVAILABLE
        if (!"AVAILABLE".equals(currentStatus)) {
            System.out.println("[updateVehicleStatusFromTimeline] Xe " + vehicleId + " đang ở trạng thái " + currentStatus + ", không cập nhật");
            return;
        }

        List<VehicleTimeline> timelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId);
        LocalDateTime now = LocalDateTime.now();

        // Kiểm tra xem có timeline RENTAL nào đang active không
        boolean hasActiveRental = timelines.stream()
                .anyMatch(t -> {
                    if (!"RENTAL".equalsIgnoreCase(t.getStatus())) return false;
                    LocalDateTime start = t.getStartTime();
                    LocalDateTime end = t.getEndTime();
                    return start != null && end != null && 
                           !now.isBefore(start) && !now.isAfter(end);
                });

        if (hasActiveRental) {
            vehicle.setStatus("RENTAL");
            vehicleRepository.save(vehicle);
            System.out.println("[updateVehicleStatusFromTimeline] Xe " + vehicleId + " → RENTAL (có timeline RENTAL đang active)");
            return;
        }

        // Kiểm tra xem có timeline BOOKED nào sớm nhất trong tương lai không
        Optional<VehicleTimeline> nextBooked = timelines.stream()
                .filter(t -> "BOOKED".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getStartTime() != null && t.getStartTime().isAfter(now))
                .min(Comparator.comparing(VehicleTimeline::getStartTime));

        if (nextBooked.isPresent()) {
            vehicle.setStatus("BOOKED");
            vehicleRepository.save(vehicle);
            System.out.println("[updateVehicleStatusFromTimeline] Xe " + vehicleId + " → BOOKED (có booking sớm nhất: " + 
                    nextBooked.get().getStartTime() + ")");
            return;
        }

        // Không có timeline nào active hoặc trong tương lai → giữ AVAILABLE
        System.out.println("[updateVehicleStatusFromTimeline] Xe " + vehicleId + " → giữ AVAILABLE (không có timeline active)");
    }
}


