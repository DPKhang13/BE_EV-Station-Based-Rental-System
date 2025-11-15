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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RentalOrderServiceImpl implements RentalOrderService {

    private final RentalOrderRepository rentalOrderRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;
    private final VehicleRepository vehicleRepository;
    private final OrderServiceRepository orderServiceRepository;
    private final VehicleModelService vehicleModelService;
    private final PricingRuleService pricingRuleService;
    private final CouponService couponService;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final VehicleTimelineRepository vehicleTimelineRepository;
    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final PhotoRepository photoRepository;
    private final PaymentRepository paymentRepository;
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
        // Kiểm tra xem xe này đã có booking nào chưa (bằng cách check timeline BOOKED)
        List<VehicleTimeline> existingBookings = vehicleTimelineRepository.findByVehicle_VehicleId(vehicle.getVehicleId())
                .stream()
                .filter(t -> "BOOKED".equalsIgnoreCase(t.getStatus()))
                .toList();

        // Nếu đây là lần đầu tiên đặt xe (không có timeline BOOKED nào) → set status = BOOKED ngay
        if (existingBookings.isEmpty()) {
            vehicle.setStatus("BOOKED");
            vehicleRepository.save(vehicle);
            System.out.println(" [createOrder] Lần đầu tiên đặt xe " + vehicle.getVehicleId() + " → Set status = BOOKED");
        } else {
            System.out.println(" [createOrder] Xe " + vehicle.getVehicleId() + " đã có booking, giữ status hiện tại");
        }

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

        // Giải phóng xe cũ
        oldVehicle.setStatus("AVAILABLE");
        vehicleRepository.save(oldVehicle);

        // Gán xe mới
        mainDetail.setVehicle(newVehicle);
        mainDetail.setStatus("SWITCHED");
        if (note != null && !note.isBlank()) {
            mainDetail.setDescription(note);
        }
        rentalOrderDetailRepository.save(mainDetail);

        // ====== CẬP NHẬT XE MỚI ======
        // Kiểm tra xem xe mới đã có booking nào chưa
        List<VehicleTimeline> existingBookings = vehicleTimelineRepository.findByVehicle_VehicleId(newVehicle.getVehicleId())
                .stream()
                .filter(t -> "BOOKED".equalsIgnoreCase(t.getStatus()))
                .toList();

        // Nếu đây là lần đầu tiên đặt xe mới (không có timeline BOOKED nào) → set status = BOOKED ngay
        if (existingBookings.isEmpty()) {
            newVehicle.setStatus("BOOKED");
            vehicleRepository.save(newVehicle);
            System.out.println(" [changeVehicle] Lần đầu tiên đặt xe " + newVehicle.getVehicleId() + " → Set status = BOOKED");
        } else {
            System.out.println(" [changeVehicle] Xe " + newVehicle.getVehicleId() + " đã có booking, giữ status hiện tại");
        }

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

        rentalOrderRepository.save(order);
        return mapToResponse(order, mainDetail);
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
                Long vehicleId = vehicle.getVehicleId();

                vehicle.setStatus("AVAILABLE");
                vehicleRepository.save(vehicle);

                // Xóa timeline khi hủy order (không cần track nữa)
                deleteTimelineForOrder(orderId, vehicleId);

                // 🔄 KIỂM TRA XE AVAILABLE: Nếu xe available, kiểm tra có booking tiếp theo thì chuyển sang BOOKED
                System.out.println("🚫 [deleteOrder] Đơn " + orderId + " bị hủy, kiểm tra nếu xe available và có hàng chờ cho xe " + vehicleId);
                checkAndTransitionToNextBooking(vehicleId);
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
    public List<OrderResponse> findByCustomer_UserIdOrderByCreatedAtDesc(UUID customerId) {
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

        // Tìm chi tiết PICKUP hoặc FULL_PAYMENT
        RentalOrderDetail pickupDetail = order.getDetails().stream()
                .filter(d -> "PICKUP".equalsIgnoreCase(d.getType()) || "FULL_PAYMENT".equalsIgnoreCase(d.getType()))
                .reduce((first, second) -> second)
                .orElse(null);

        if (pickupDetail == null)
            throw new BadRequestException("Không tìm thấy chi tiết thanh toán (PICKUP hoặc FULL_PAYMENT) trong đơn thuê");

        //  Nếu chưa thanh toán phần còn lại (chưa SUCCESS) thì chặn
        if (!"SUCCESS".equalsIgnoreCase(pickupDetail.getStatus()))
            throw new BadRequestException("Khách hàng chưa thanh toán — không thể bàn giao xe");

        //  Lấy chi tiết chính (RENTAL)
        RentalOrderDetail mainDetail = getMainDetail(order);
        if (mainDetail == null)
            throw new BadRequestException("Không tìm thấy chi tiết đơn thuê chính (RENTAL)");

        //  Lấy xe
        Vehicle vehicle = mainDetail.getVehicle();
        if (vehicle == null)
            throw new BadRequestException("Không tìm thấy xe trong chi tiết đơn");

        //  ✅ Kiểm tra xe không đang được người khác thuê
        if ("RENTAL".equalsIgnoreCase(vehicle.getStatus())) {
            // Tìm đơn RENTAL đang hoạt động của xe này (không phải đơn hiện tại)
            List<RentalOrderDetail> activeRentals = rentalOrderDetailRepository
                    .findByVehicle_VehicleIdAndStatusIn(vehicle.getVehicleId(), List.of("RENTAL"));

            boolean isRentedByOther = activeRentals.stream()
                    .anyMatch(d -> d.getOrder() != null
                            && !d.getOrder().getOrderId().equals(orderId)
                            && "RENTAL".equalsIgnoreCase(d.getOrder().getStatus()));

            if (isRentedByOther) {
                throw new ConflictException("Xe đang được khách hàng khác thuê. Không thể bàn giao xe!");
            }
        }

        //  Cập nhật trạng thái — KHÔNG tạo thêm detail nào
        order.setStatus("RENTAL");
        vehicle.setStatus("RENTAL");

        //  Lưu DB
        rentalOrderDetailRepository.save(mainDetail);
        vehicleRepository.save(vehicle);
        rentalOrderRepository.save(order);

        //  Lưu lịch sử vào timeline
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

        // Tăng pickup_count cho staff hiện tại (nếu có)
        UUID staffId = getCurrentStaffId();
        System.out.println("🔍 [confirmPickup] staffId from JWT: " + staffId);
        if (staffId != null) {
            System.out.println("🔍 [confirmPickup] Calling incrementPickupCount...");
            incrementPickupCount(staffId);
        } else {
            System.out.println("⚠️ [confirmPickup] staffId is null, skip incrementPickupCount");
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
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(model.getSeatCount(), model.getVariant());

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

        // GIỮ NGUYÊN totalPrice đã thanh toán trước đó
        // Chỉ tính phí trễ nếu trả muộn
        if (actualDays > expectedDays) {
            long lateDays = actualDays - expectedDays;
            BigDecimal lateFee = rule.getLateFeePerDay().multiply(BigDecimal.valueOf(lateDays));

            // Tạo OrderService cho phí trễ
            OrderService lateService = OrderService.builder()
                    .order(order)
                    .vehicle(vehicle)
                    .serviceType("LATE_FEE")
                    .description("Trả xe trễ " + lateDays + " ngày")
                    .cost(lateFee)
                    .status("PENDING")
                    .occurredAt(actualReturnTime)
                    .build();
            orderServiceRepository.save(lateService);

            System.out.println("⚠️ Khách trả xe trễ " + lateDays + " ngày, phí trễ: " + lateFee);
        } else if (actualDays < expectedDays) {
            System.out.println("✅ Khách trả xe sớm " + (expectedDays - actualDays) + " ngày");
        }

        // KHÔNG thay đổi mainDetail.price - giữ nguyên giá đã tính từ lúc đặt xe
        // mainDetail.setPrice() - KHÔNG cần update
        // rentalOrderDetailRepository.save(mainDetail) - KHÔNG cần save

        // Kiểm tra xem có service nào cần thanh toán không
        List<OrderService> pendingServices = orderServiceRepository
                .findByOrder_OrderId(orderId)
                .stream()
                .filter(s -> "PENDING".equalsIgnoreCase(s.getStatus()))
                .toList();

        // Nếu KHÔNG có service nào → hoàn tất đơn luôn
        if (pendingServices.isEmpty()) {
            vehicle.setStatus("CHECKING");
            order.setStatus("COMPLETED");

            // Xóa timeline khi order hoàn thành (xe đã trả, không cần track nữa)
            deleteTimelineForOrder(orderId, vehicle.getVehicleId());

            // 🔄 KIỂM TRA XE AVAILABLE: Nếu xe available, kiểm tra có timeline đầu tiên thì chuyển sang BOOKED
            checkAndTransitionToNextBooking(vehicle.getVehicleId());
        } else {
            // Nếu CÓ service → chờ thanh toán
            vehicle.setStatus("CHECKING");
            order.setStatus("PENDING_FINAL_PAYMENT"); // Chờ thanh toán type 5 (services + phí trễ)

            // Tạo timeline CHECKING để track xe đang được kiểm tra
            createCheckingTimeline(vehicle, order, "Xe đang được kiểm tra sau khi trả");
        }

        vehicleRepository.save(vehicle);
        // GIỮ NGUYÊN order.totalPrice - không thay đổi giá đã thanh toán
        rentalOrderRepository.save(order);

        // Tăng return_count cho staff hiện tại (nếu có)
        UUID staffId = getCurrentStaffId();
        System.out.println("🔍 [confirmReturn] staffId from JWT: " + staffId);
        if (staffId != null) {
            System.out.println("🔍 [confirmReturn] Calling incrementReturnCount...");
            incrementReturnCount(staffId);
        } else {
            System.out.println("⚠️ [confirmReturn] staffId is null, skip incrementReturnCount");
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
        PricingRule rule = pricingRuleService.getPricingRuleBySeatAndVariant(model.getSeatCount(), model.getVariant());

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
                            || s.equals("PAID")
                            || s.equals("RENTAL")              // đang thuê
                            || s.equals("DEPOSITED")
                            || s.equals("SERVICE_PAID") // đã đặt cọc
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
            BigDecimal totalServiceCost = Optional.ofNullable(order.getServices())
                    .orElse(List.of()).stream()
                    .map(s -> Optional.ofNullable(s.getCost()).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Tổng tiền = order.totalPrice (giá thuê) + service cost
            BigDecimal totalPrice = Optional.ofNullable(order.getTotalPrice()).orElse(BigDecimal.ZERO)
                    .add(totalServiceCost);

            // Tổng đã thanh toán
            BigDecimal totalPaid = Optional.ofNullable(order.getPayments())
                    .orElse(List.of()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                    .map(p -> Optional.ofNullable(p.getAmount()).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Còn lại = Lấy từ payment deposit nếu c��, không thì tính = total - paid
            BigDecimal remainingAmount = Optional.ofNullable(order.getPayments())
                    .orElse(List.of()).stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst()
                    .map(p -> Optional.ofNullable(p.getRemainingAmount()).orElse(BigDecimal.ZERO))
                    .orElse(totalPrice.subtract(totalPaid));
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
            System.err.println("⚠️ Không thể lấy userId từ JWT: " + e.getMessage());
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
                System.out.println("✅ Đã cập nhật pickup_count: " + oldCount + " → " + (oldCount + 1) +
                        " cho staff " + staffId + " vào ca " + shiftTime);
            } else {
                // Nếu không tìm thấy schedule, tự động tạo mới
                System.out.println("⚠️ Không tìm thấy schedule cho staff " + staffId +
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
                    System.out.println("✅ Đã tự động tạo schedule mới và cập nhật pickup_count = 1");
                } else {
                    System.err.println("❌ Không thể tạo schedule: Staff không có station");
                }
            }
        } catch (Exception e) {
            // Log error nhưng không throw exception để không ảnh hưởng flow chính
            System.err.println("❌ Failed to increment pickup count: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Tăng return_count cho staff trong ca làm việc hiện tại
     */
    private void incrementReturnCount(UUID staffId) {
        System.out.println("🔍 [incrementReturnCount] START - staffId: " + staffId);
        try {
            String shiftTime = getCurrentShiftTime();
            java.time.LocalDate today = java.time.LocalDate.now();
            System.out.println("🔍 [incrementReturnCount] Shift: " + shiftTime + ", Date: " + today);

            Optional<EmployeeSchedule> scheduleOpt =
                    employeeScheduleRepository.findByStaff_UserIdAndShiftDateAndShiftTime(
                            staffId, today, shiftTime);

            if (scheduleOpt.isPresent()) {
                EmployeeSchedule schedule = scheduleOpt.get();
                int oldCount = schedule.getReturnCount();
                schedule.setReturnCount(oldCount + 1);
                employeeScheduleRepository.save(schedule);
                System.out.println("✅ Đã cập nhật return_count: " + oldCount + " → " + (oldCount + 1) +
                        " cho staff " + staffId + " vào ca " + shiftTime);
            } else {
                // Nếu không tìm thấy schedule, tự động tạo mới
                System.out.println("⚠️ Không tìm thấy schedule cho staff " + staffId +
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
                    System.out.println("✅ Đã tự động tạo schedule mới và cập nhật return_count = 1");
                } else {
                    System.err.println("❌ Không thể tạo schedule: Staff không có station");
                }
            }
        } catch (Exception e) {
            // Log error nhưng không throw exception để không ảnh hưởng flow chính
            System.err.println("❌ Failed to increment return count: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("🔍 [incrementReturnCount] END");
    }

    /**
     * Kiểm tra xem xe có booking trùng lặp trong khoảng thời gian không
     * Cho phép multiple bookings nếu thời gian không trùng nhau
     * Status: pending | confirmed | active | done | cancelled
     */
    private boolean hasOverlappingActiveBooking(Long vehicleId, LocalDateTime requestStart, LocalDateTime requestEnd) {
        System.out.println("🔍 [hasOverlappingActiveBooking] Kiểm tra xe " + vehicleId +
                         " cho thời gian: [" + requestStart + " - " + requestEnd + "]");

        // Lấy tất cả chi tiết đơn đang ACTIVE (pending, confirmed, active - không including done/cancelled)
        List<RentalOrderDetail> activeDetails = rentalOrderDetailRepository
                .findByVehicle_VehicleIdAndStatusIn(vehicleId, List.of("pending", "confirmed", "active"));

        System.out.println("📋 Tổng booking active: " + activeDetails.size());

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
                    System.out.println("⚠️ ❌ Có booking trùng lặp: [" + existingStart + " - " + existingEnd +
                                     "] với request [" + requestStart + " - " + requestEnd + "]");
                    return true; // Có overlap với booking đang active
                } else {
                    System.out.println("✅ Không trùng lặp");
                }
            }
        }

        System.out.println("✅ ✅ Không có booking trùng lặp cho xe " + vehicleId);
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

    /**
     * Xóa tất cả timeline của xe (khi staff chuyển xe về AVAILABLE)
     */
    private void deleteAllTimelinesForVehicle(Long vehicleId) {
        if (vehicleId == null) return;

        List<VehicleTimeline> timelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId);
        if (!timelines.isEmpty()) {
            vehicleTimelineRepository.deleteAll(timelines);
        }
    }

    /**
     * Tạo timeline CHECKING khi xe cần kiểm tra sau khi trả
     */
    private void createCheckingTimeline(Vehicle vehicle, RentalOrder order, String note) {
        // Xóa timeline cũ của order này trước
        deleteTimelineForOrder(order.getOrderId(), vehicle.getVehicleId());

        LocalDateTime now = LocalDateTime.now();
        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(vehicle)
                .order(order)
                .day(now.toLocalDate())
                .startTime(now)
                .endTime(now.plusDays(1)) // Dự kiến kiểm tra trong 1 ngày
                .status("CHECKING")
                .sourceType("VEHICLE_CHECKING")
                .note(note)
                .updatedAt(now)
                .build();
        vehicleTimelineRepository.save(timeline);
    }

    /**
     * Tạo timeline MAINTENANCE khi xe cần bảo trì
     */
    private void createMaintenanceTimeline(Vehicle vehicle, String note, LocalDateTime endTime) {
        // Chỉ xóa timeline MAINTENANCE/CHECKING cũ, giữ lại booking timeline
        deleteMaintenanceAndCheckingTimelines(vehicle.getVehicleId());

        LocalDateTime now = LocalDateTime.now();
        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(vehicle)
                .day(now.toLocalDate())
                .startTime(now)
                .endTime(endTime != null ? endTime : now.plusDays(3)) // Mặc định bảo trì 3 ngày
                .status("MAINTENANCE")
                .sourceType("VEHICLE_MAINTENANCE")
                .note(note != null ? note : "Xe đang bảo trì")
                .updatedAt(now)
                .build();
        vehicleTimelineRepository.save(timeline);
    }

    /**
     * Xóa chỉ timeline MAINTENANCE và CHECKING (giữ lại timeline booking)
     */
    private void deleteMaintenanceAndCheckingTimelines(Long vehicleId) {
        if (vehicleId == null) return;

        List<VehicleTimeline> timelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId);

        // Chỉ xóa timeline có sourceType là VEHICLE_MAINTENANCE hoặc VEHICLE_CHECKING
        List<VehicleTimeline> toDelete = timelines.stream()
                .filter(t -> "VEHICLE_MAINTENANCE".equals(t.getSourceType())
                        || "VEHICLE_CHECKING".equals(t.getSourceType()))
                .collect(Collectors.toList());

        if (!toDelete.isEmpty()) {
            vehicleTimelineRepository.deleteAll(toDelete);
        }
    }

    /**
     * Kiểm tra timeline tiếp theo của xe và tự động chuyển trạng thái
     * Nếu có booking pending/confirmed tiếp theo → set xe thành BOOKED
     */

    private void checkAndTransitionToNextBooking(Long vehicleId) {
        System.out.println("🔄 [checkAndTransitionToNextBooking] Kiểm tra xe " + vehicleId);

        // Bước 1: Kiểm tra trạng thái xe hiện tại
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId);
        if (vehicleOpt.isEmpty()) {
            System.out.println("❌ Không tìm thấy xe " + vehicleId);
            return;
        }

        Vehicle vehicle = vehicleOpt.get();
        String currentStatus = vehicle.getStatus();
        System.out.println("📍 Trạng thái xe hiện tại: " + currentStatus);

        // Bước 2: Chỉ kiểm tra và chuyển đổi NẾU xe đang AVAILABLE
        if (!"AVAILABLE".equals(currentStatus)) {
            System.out.println("⏸️ Xe không ở trạng thái AVAILABLE, bỏ qua kiểm tra booking");
            return;
        }

        // Bước 3: Lấy tất cả booking pending của xe này (chưa active)
        List<RentalOrderDetail> pendingBookings = rentalOrderDetailRepository
                .findByVehicle_VehicleIdAndStatusIn(vehicleId, List.of("PENDING", "CONFIRMED"));

        if (pendingBookings.isEmpty()) {
            System.out.println("✅ Xe AVAILABLE, không có booking tiếp theo trong hàng chờ");
            return;
        }

        // Bước 4: Lấy booking sớm nhất (theo startTime)
        RentalOrderDetail nextBooking = pendingBookings.stream()
                .min(java.util.Comparator.comparing(RentalOrderDetail::getStartTime))
                .orElse(null);

        if (nextBooking != null) {
            LocalDateTime nextStart = nextBooking.getStartTime();
            LocalDateTime nextEnd = nextBooking.getEndTime();

            System.out.println("📅 Booking tiếp theo: [" + nextStart + " - " + nextEnd +
                             "] Status: " + nextBooking.getStatus());

            // 🔄 Tự động set xe = BOOKED luôn (xe đang AVAILABLE và có booking trong hàng chờ)
            System.out.println("⏰ Xe AVAILABLE → Chuyển sang BOOKED cho booking tiếp theo");

            vehicle.setStatus("BOOKED");
            vehicleRepository.save(vehicle);

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
                    .sourceType("AUTO_QUEUE_TRANSITION")
                    .note("Tự động chuyển từ hàng chờ để chuẩn bị cho booking #" + nextBooking.getOrder().getOrderId())
                    .updatedAt(now)
                    .build();
            vehicleTimelineRepository.save(timeline);

            System.out.println("✅ Xe " + vehicleId + " = BOOKED cho booking tiếp theo");
        }
    }
}


