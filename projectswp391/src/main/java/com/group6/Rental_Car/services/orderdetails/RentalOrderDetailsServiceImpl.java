package com.group6.Rental_Car.services.orderdetails;

import com.group6.Rental_Car.dtos.orderdetail.OrderDetailCreateRequest;
import com.group6.Rental_Car.dtos.orderdetail.OrderDetailResponse;
import com.group6.Rental_Car.entities.Payment;
import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.entities.RentalOrderDetail;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleModel;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.PaymentRepository;
import com.group6.Rental_Car.repositories.RentalOrderDetailRepository;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import com.group6.Rental_Car.services.vehicle.VehicleModelService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


    @Service
    @RequiredArgsConstructor
    @Transactional
    public class RentalOrderDetailsServiceImpl implements RentalOrderDetailService {

        private final RentalOrderDetailRepository rentalOrderDetailRepository;
        private final RentalOrderRepository rentalOrderRepository;
        private final VehicleRepository vehicleRepository;
        private final ModelMapper modelMapper;
        private final PaymentRepository paymentRepository;
        private final VehicleModelService vehicleModelService;

        // =====================================================
        // CREATE DETAIL (Admin/Staff tạo thủ công)
        // =====================================================
        @Override
        public OrderDetailResponse createDetail(OrderDetailCreateRequest request) {

            RentalOrder order = rentalOrderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

            Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe"));

            RentalOrderDetail detail = RentalOrderDetail.builder()
                    .order(order)
                    .vehicle(vehicle)
                    .type(request.getType().trim().toUpperCase())
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .price(request.getPrice())
                    .description(request.getDescription())
                    .status("PENDING")
                    .build();

            return toResponse(rentalOrderDetailRepository.save(detail));
        }

        // =====================================================

        // =====================================================
        @Override
        public OrderDetailResponse updateDetail(Long detailId, OrderDetailCreateRequest request) {

            RentalOrderDetail existing = rentalOrderDetailRepository.findById(detailId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi tiết thuê"));

            RentalOrder order = rentalOrderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

            Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe"));

            // Lưu giá cũ để tính toán remainingAmount
            BigDecimal oldPrice = existing.getPrice() != null ? existing.getPrice() : BigDecimal.ZERO;
            BigDecimal newPrice = request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO;
            BigDecimal priceDifference = newPrice.subtract(oldPrice);

            existing.setOrder(order);
            existing.setVehicle(vehicle);
            existing.setType(request.getType().trim().toUpperCase());
            existing.setStartTime(request.getStartTime());
            existing.setEndTime(request.getEndTime());
            existing.setPrice(request.getPrice());
            existing.setDescription(request.getDescription());

            RentalOrderDetail savedDetail = rentalOrderDetailRepository.save(existing);

            // Nếu detail là SERVICE và status là PENDING, cập nhật remainingAmount của payment
            // và totalPrice của order
            if ("SERVICE".equalsIgnoreCase(existing.getType()) && "PENDING".equalsIgnoreCase(existing.getStatus())) {
                // Cập nhật totalPrice của order
                BigDecimal currentTotal = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
                BigDecimal updatedTotal = currentTotal.add(priceDifference);
                order.setTotalPrice(updatedTotal);
                rentalOrderRepository.save(order);

                // Cập nhật remainingAmount của payment (DEPOSIT hoặc FULL_PAYMENT)
                List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(order.getOrderId());

                Optional<Payment> depositPayment = payments.stream()
                        .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                        .findFirst();

                if (depositPayment.isPresent()) {
                    Payment deposit = depositPayment.get();
                    BigDecimal currentRemaining = deposit.getRemainingAmount() != null
                            ? deposit.getRemainingAmount()
                            : BigDecimal.ZERO;
                    deposit.setRemainingAmount(currentRemaining.add(priceDifference));
                    paymentRepository.save(deposit);
                    System.out.println("✅ [updateDetail] Đã cập nhật remainingAmount cho deposit payment: " +
                            currentRemaining + " + " + priceDifference + " = " + deposit.getRemainingAmount());
                } else {
                    Optional<Payment> fullPayment = payments.stream()
                            .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                            .findFirst();

                    if (fullPayment.isPresent()) {
                        Payment full = fullPayment.get();
                        BigDecimal currentRemaining = full.getRemainingAmount() != null
                                ? full.getRemainingAmount()
                                : BigDecimal.ZERO;
                        full.setRemainingAmount(currentRemaining.add(priceDifference));
                        paymentRepository.save(full);
                        System.out.println("✅ [updateDetail] Đã cập nhật remainingAmount cho full payment: " +
                                currentRemaining + " + " + priceDifference + " = " + full.getRemainingAmount());
                    }
                }
            }

            return toResponse(savedDetail);
        }

        // =====================================================
        //  DELETE DETAIL
        // =====================================================
        @Override
        public void deleteDetail(Long detailId) {
            RentalOrderDetail existing = rentalOrderDetailRepository.findById(detailId)
                    .orElseThrow(() -> new ResourceNotFoundException("Chi tiết thuê không tồn tại"));

            // Lưu thông tin detail trước khi xóa để cập nhật remainingAmount
            String detailType = existing.getType();
            String detailStatus = existing.getStatus();
            BigDecimal detailPrice = existing.getPrice() != null ? existing.getPrice() : BigDecimal.ZERO;
            RentalOrder order = existing.getOrder();

            // Xóa detail
            rentalOrderDetailRepository.deleteById(detailId);

            // Nếu detail là SERVICE và status là PENDING, cập nhật remainingAmount của payment
            // và totalPrice của order
            if ("SERVICE".equalsIgnoreCase(detailType) && "PENDING".equalsIgnoreCase(detailStatus)) {
                // Trừ detailPrice khỏi totalPrice của order
                BigDecimal currentTotal = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
                BigDecimal updatedTotal = currentTotal.subtract(detailPrice);
                if (updatedTotal.compareTo(BigDecimal.ZERO) < 0) {
                    updatedTotal = BigDecimal.ZERO;
                }
                order.setTotalPrice(updatedTotal);
                rentalOrderRepository.save(order);

                // Trừ detailPrice khỏi remainingAmount của payment (DEPOSIT hoặc FULL_PAYMENT)
                List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(order.getOrderId());

                Optional<Payment> depositPayment = payments.stream()
                        .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                        .findFirst();

                if (depositPayment.isPresent()) {
                    Payment deposit = depositPayment.get();
                    BigDecimal currentRemaining = deposit.getRemainingAmount() != null
                            ? deposit.getRemainingAmount()
                            : BigDecimal.ZERO;
                    BigDecimal updatedRemaining = currentRemaining.subtract(detailPrice);
                    if (updatedRemaining.compareTo(BigDecimal.ZERO) < 0) {
                        updatedRemaining = BigDecimal.ZERO;
                    }
                    deposit.setRemainingAmount(updatedRemaining);
                    paymentRepository.save(deposit);
                    System.out.println("✅ [deleteDetail] Đã cập nhật remainingAmount cho deposit payment: " +
                            currentRemaining + " - " + detailPrice + " = " + deposit.getRemainingAmount());
                } else {
                    Optional<Payment> fullPayment = payments.stream()
                            .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                            .findFirst();

                    if (fullPayment.isPresent()) {
                        Payment full = fullPayment.get();
                        BigDecimal currentRemaining = full.getRemainingAmount() != null
                                ? full.getRemainingAmount()
                                : BigDecimal.ZERO;
                        BigDecimal updatedRemaining = currentRemaining.subtract(detailPrice);
                        if (updatedRemaining.compareTo(BigDecimal.ZERO) < 0) {
                            updatedRemaining = BigDecimal.ZERO;
                        }
                        full.setRemainingAmount(updatedRemaining);
                        paymentRepository.save(full);
                        System.out.println("✅ [deleteDetail] Đã cập nhật remainingAmount cho full payment: " +
                                currentRemaining + " - " + detailPrice + " = " + full.getRemainingAmount());
                    }
                }
            }
        }

        // =====================================================
        //  STAFF VIEW — chỉ xem DEPOSIT + PICKUP
        // =====================================================
        @Override
        public List<OrderDetailResponse> getDetailsByOrderStaff(UUID orderId) {

            rentalOrderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

            return rentalOrderDetailRepository.findByOrder_OrderId(orderId)
                    .stream()
                    .filter(d -> {
                        String type = safeType(d);
                        return type.equals("DEPOSIT") || type.equals("PICKUP");
                    })
                    .map(this::toResponse)
                    .sorted(Comparator.comparing(OrderDetailResponse::getStartTime))
                    .collect(Collectors.toList());
        }

        // =====================================================
        //  CUSTOMER VIEW
        // RULE:
        // - Nếu order chưa thanh toán gì → SHOW RENTAL (chỉ RENTAL)
        // - Nếu đã thanh toán → ẨN RENTAL và chỉ show DEPOSIT/PICKUP/FULL_PAYMENT/REFUND
        // =====================================================
        @Override
        public List<OrderDetailResponse> getDetailsByOrder(UUID orderId) {

            RentalOrder order = rentalOrderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

            String status = order.getStatus().toUpperCase();
            // Lấy danh sách payments để map với từng detail type
            List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(order.getOrderId());
            
            // Kiểm tra có payment CASH PENDING không (để hiển thị detail PENDING)
            boolean hasCashPending = payments.stream()
                    .anyMatch(p -> "CASH".equalsIgnoreCase(p.getMethod()) && p.getStatus() == PaymentStatus.PENDING);
            
            // Show RENTAL khi: PENDING (chưa thanh toán), CREATED, BOOKED
            // Show TẤT CẢ khi: FAILED (để hiển thị đầy đủ thông tin đơn đã hủy)
            // Show payment details khi: có payment CASH PENDING hoặc đã thanh toán
            boolean showOnlyRental =
                    status.equals("PENDING") ||
                    status.equals("CREATED") ||
                    status.equals("BOOKED");
            boolean showAll = status.equals("FAILED");
            boolean showPaymentDetails = hasCashPending || 
                    status.equals("PENDING_DEPOSIT") ||
                    status.equals("PENDING_FINAL") ||
                    status.equals("PENDING_FULL_PAYMENT") ||
                    status.equals("DEPOSITED") ||
                    status.equals("AWAITING") ||              // đã thanh toán đặt cọc, chờ nhận xe
                    status.equals("PAID") ||                  // đã thanh toán hết dịch vụ
                    status.equals("RENTAL") ||
                    status.equals("COMPLETED");

            List<RentalOrderDetail> raw = rentalOrderDetailRepository.findByOrder_OrderId(orderId);

            // Lấy số tiền còn lại chưa thanh toán từ Payment
            // Logic mới: remainingAmount đã bao gồm cả dịch vụ (không cần cộng thêm SERVICE PENDING)
            BigDecimal remainingAmount;
            
            // Kiểm tra FULL_PAYMENT (type 3) SUCCESS
            Optional<Payment> fullPayment = payments.stream()
                    .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();
            
            if (fullPayment.isPresent()) {
                BigDecimal remaining = fullPayment.get().getRemainingAmount();
                remainingAmount = remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 
                        ? remaining 
                        : BigDecimal.ZERO;
            } else {
                // Kiểm tra FINAL_PAYMENT (type 2) SUCCESS
                boolean hasFinalPaymentSuccess = payments.stream()
                        .anyMatch(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.SUCCESS);
                if (hasFinalPaymentSuccess) {
                    // Đã thanh toán PICKUP, kiểm tra xem DEPOSIT còn remainingAmount không (dịch vụ mới)
                    Optional<Payment> depositPayment = payments.stream()
                            .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                            .findFirst();
                    
                    if (depositPayment.isPresent()) {
                        BigDecimal remaining = depositPayment.get().getRemainingAmount();
                        remainingAmount = remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 
                                ? remaining 
                                : BigDecimal.ZERO;
                    } else {
                        remainingAmount = BigDecimal.ZERO;
                    }
                } else {
                    // Kiểm tra DEPOSIT (type 1) SUCCESS
                    Optional<Payment> depositPayment = payments.stream()
                            .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                            .findFirst();
                    
                    if (depositPayment.isPresent()) {
                        BigDecimal remaining = depositPayment.get().getRemainingAmount();
                        remainingAmount = remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 
                                ? remaining 
                                : BigDecimal.ZERO;
                    } else {
                        // Chưa thanh toán gì → trả về totalPrice
                        remainingAmount = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
                    }
                }
            }

            // Kiểm tra có PICKUP SUCCESS không và đã thanh toán hết chưa
            boolean hasPickupSuccess = raw.stream()
                    .anyMatch(d -> "PICKUP".equalsIgnoreCase(safeType(d)) && "SUCCESS".equalsIgnoreCase(d.getStatus()));
            boolean isFullyPaid = remainingAmount.compareTo(BigDecimal.ZERO) == 0;
            
            List<OrderDetailResponse> details = raw.stream()
                    .filter(d -> {
                        // Nếu FAILED, show tất cả detail
                        if (showAll) return true;
                        
                        String type = safeType(d);
                        String detailStatus = d.getStatus() != null ? d.getStatus().toUpperCase() : "";
                        
                        // Nếu showOnlyRental → chỉ show RENTAL
                        if (showOnlyRental) return type.equals("RENTAL");
                        
                        // Nếu có payment CASH PENDING → show payment details (DEPOSIT/PICKUP/FULL_PAYMENT) và SERVICE, ẩn RENTAL
                        if (hasCashPending) {
                            return type.equals("DEPOSIT") || 
                                   type.equals("PICKUP") || 
                                   type.equals("FULL_PAYMENT") ||
                                   type.equals("SERVICE");
                        }
                        
                        // Nếu đã thanh toán hết và có PICKUP SUCCESS → ẩn PICKUP PENDING
                        if (isFullyPaid && hasPickupSuccess && type.equals("PICKUP") && "PENDING".equalsIgnoreCase(detailStatus)) {
                            return false;
                        }
                        
                        // Nếu showPaymentDetails → show payment details và SERVICE, không show RENTAL
                        if (showPaymentDetails) {
                            // Nếu đã thanh toán hết và có PICKUP SUCCESS → ẩn PICKUP PENDING
                            if (isFullyPaid && hasPickupSuccess && type.equals("PICKUP") && "PENDING".equalsIgnoreCase(detailStatus)) {
                                return false;
                            }
                            // Luôn ẩn RENTAL detail khi đã thanh toán
                            // Chỉ show payment details (DEPOSIT, PICKUP, FULL_PAYMENT, REFUND) và SERVICE
                            return type.equals("DEPOSIT") || 
                                   type.equals("PICKUP") || 
                                   type.equals("FULL_PAYMENT") ||
                                   type.equals("REFUND") ||
                                   type.equals("SERVICE");
                        }
                        
                        // Mặc định: không show RENTAL
                        return !type.equals("RENTAL");
                    })
                    .map(d -> {
                        OrderDetailResponse dto = toResponse(d, order);
                        
                        // Set payment method cho các detail
                        // SERVICE detail: lấy từ payment type 2 (final payment) khi thanh toán dịch vụ
                        String detailType = safeType(d);
                        String methodPayment = null;
                        
                        if ("SERVICE".equals(detailType)) {
                            // SERVICE detail: mặc định methodPayment = null
                            methodPayment = null;
                            
                            // Chỉ set payment method khi detail status là SUCCESS và có payment SUCCESS
                            String detailStatus = d.getStatus() != null ? d.getStatus() : "";
                            if ("SUCCESS".equalsIgnoreCase(detailStatus)) {
                                // Ưu tiên payment type 5 (service payment), sau đó mới lấy payment type 2 (final payment)
                                List<Payment> servicePayments = payments.stream()
                                        .filter(p -> (p.getPaymentType() == 5 || p.getPaymentType() == 2))
                                        .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                                        .sorted(Comparator.comparing((Payment p) -> p.getPaymentType() == 5 ? 0 : 1)
                                                .thenComparing(Payment::getPaymentId))
                                        .collect(Collectors.toList());
                                
                                if (!servicePayments.isEmpty()) {
                                    Payment successPayment = servicePayments.get(0);
                                    String method = successPayment.getMethod();
                                    if (method != null) {
                                        if (method.equalsIgnoreCase("CASH")) {
                                            methodPayment = "CASH";
                                        } else if (method.equalsIgnoreCase("captureWallet") || 
                                                   method.equalsIgnoreCase("payWithMethod") || 
                                                   method.equalsIgnoreCase("momo")) {
                                            methodPayment = "MoMo";
                                        } else {
                                            methodPayment = method;
                                        }
                                    }
                                }
                            }
                        } else {
                            // DEPOSIT, PICKUP, FULL_PAYMENT: tìm payment method tương ứng với detail type
                            // Logic: 
                            // 1. Ưu tiên payment SUCCESS trước (đã thanh toán) - lấy payment cũ nhất (theo paymentId)
                            // 2. Chỉ lấy PENDING nếu không có SUCCESS nào
                            List<Payment> filteredPayments = payments.stream()
                                    .filter(p -> {
                                        // Map detail type với payment type
                                        if ("DEPOSIT".equals(detailType) && p.getPaymentType() == 1) return true;
                                        if ("PICKUP".equals(detailType) && p.getPaymentType() == 2) return true;
                                        if ("FULL_PAYMENT".equals(detailType) && p.getPaymentType() == 3) return true;
                                        return false;
                                    })
                                    .collect(Collectors.toList());
                            
                            // Tìm payment SUCCESS - ưu tiên payment cũ nhất (đã có từ trước)
                            // Để giữ nguyên payment method đã có, không thay đổi khi thanh toán cuối bằng CASH
                            List<Payment> successPayments = filteredPayments.stream()
                                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                                    .sorted(Comparator.comparing(Payment::getPaymentId)) // Payment cũ trước
                                    .collect(Collectors.toList());
                            
                            if (!successPayments.isEmpty()) {
                                // Ưu tiên payment cũ nhất (payment đầu tiên) để giữ nguyên payment method đã có
                                // Nếu có nhiều payment (ví dụ: MoMo cũ + CASH mới), lấy payment cũ nhất
                                Payment firstSuccessPayment = successPayments.get(0);
                                String method = firstSuccessPayment.getMethod();
                                if (method != null) {
                                    if (method.equalsIgnoreCase("CASH")) {
                                        methodPayment = "CASH";
                                    } else if (method.equalsIgnoreCase("captureWallet") || 
                                               method.equalsIgnoreCase("payWithMethod") || 
                                               method.equalsIgnoreCase("momo")) {
                                        methodPayment = "MoMo";
                                    } else {
                                        methodPayment = method;
                                    }
                                }
                            } else {
                                // Chưa thanh toán → lấy method từ payment PENDING (nếu có)
                                // Ưu tiên payment cũ nhất
                                List<Payment> pendingPayments = filteredPayments.stream()
                                        .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                                        .sorted(Comparator.comparing(Payment::getPaymentId))
                                        .collect(Collectors.toList());
                                
                                if (!pendingPayments.isEmpty()) {
                                    Payment firstPendingPayment = pendingPayments.get(0);
                                    String method = firstPendingPayment.getMethod();
                                    if (method != null) {
                                        if (method.equalsIgnoreCase("CASH")) {
                                            methodPayment = "CASH";
                                        } else if (method.equalsIgnoreCase("captureWallet") || 
                                                   method.equalsIgnoreCase("payWithMethod") || 
                                                   method.equalsIgnoreCase("momo")) {
                                            methodPayment = "MoMo";
                                        } else {
                                            methodPayment = method;
                                        }
                                    }
                                }
                            }
                        }
                        
                        dto.setMethodPayment(methodPayment);
                        
                        // Chỉ hiển thị remainingAmount cho các detail thanh toán
                        boolean isPaymentDetail = detailType.equals("DEPOSIT")
                                || detailType.equals("PICKUP")
                                || detailType.equals("FULL_PAYMENT")
                                || detailType.equals("REFUND");
                        boolean isPendingRental = detailType.equals("RENTAL") && showOnlyRental;

                        dto.setRemainingAmount((isPaymentDetail || isPendingRental) ? remainingAmount : null);
                        return dto;
                    })
                    .collect(Collectors.toList());

            // =====================================================
            //  KHÔNG MERGE SERVICE vào details
            //  Services sẽ được lấy riêng qua API /api/order-services/order/{orderId}
            // =====================================================

            return details.stream()
                    .sorted(Comparator.comparing(OrderDetailResponse::getStartTime))
                    .collect(Collectors.toList());
        }

        // =====================================================
        //  GET BY VEHICLE
        // =====================================================
        @Override
        public List<OrderDetailResponse> getDetailsByVehicle(Long vehicleId) {
            return rentalOrderDetailRepository.findByVehicle_VehicleId(vehicleId)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        // =====================================================
        //  ACTIVE DETAILS by vehicle
        // =====================================================
        @Override
        public List<OrderDetailResponse> getActiveDetailsByVehicle(Long vehicleId) {

            List<String> active = List.of("PENDING", "SUCCESS", "FAILED");

            return rentalOrderDetailRepository.findByVehicle_VehicleIdAndStatusIn(vehicleId, active)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        // =====================================================
        //  ACTIVE DETAILS by order
        // =====================================================
        @Override
        public List<OrderDetailResponse> getActiveDetailsByOrder(UUID orderId) {

            List<String> active = List.of("PENDING", "SUCCESS", "FAILED");

            return rentalOrderDetailRepository.findByOrder_OrderIdAndStatusIn(orderId, active)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        // =====================================================
        // Private helper
        // =====================================================
        private String safeType(RentalOrderDetail d) {
            return Optional.ofNullable(d.getType())
                    .map(t -> t.trim().toUpperCase())
                    .orElse("");
        }

        private OrderDetailResponse toResponse(RentalOrderDetail detail) {
            return toResponse(detail, detail.getOrder());
        }

        private OrderDetailResponse toResponse(RentalOrderDetail detail, RentalOrder order) {
            OrderDetailResponse dto = modelMapper.map(detail, OrderDetailResponse.class);
            dto.setOrderId(order.getOrderId());
            dto.setVehicleId(detail.getVehicle().getVehicleId());
            
            // Thông tin khách hàng
            if (order.getCustomer() != null) {
                dto.setCustomerName(order.getCustomer().getFullName());
                dto.setPhone(order.getCustomer().getPhone());
                dto.setEmail(order.getCustomer().getEmail());
            }
            
            // Thông tin xe
            Vehicle vehicle = detail.getVehicle();
            if (vehicle != null) {
                dto.setVehicleName(vehicle.getVehicleName());
                dto.setPlateNumber(vehicle.getPlateNumber());
                dto.setVehicleStatus(vehicle.getStatus());
                
                // Thông tin trạm
                if (vehicle.getRentalStation() != null) {
                    dto.setStationName(vehicle.getRentalStation().getName());
                }
                
                // Lấy thông tin từ VehicleModel
                VehicleModel model = vehicleModelService.findByVehicle(vehicle);
                if (model != null) {
                    dto.setColor(model.getColor());
                    dto.setCarmodel(model.getCarmodel());
                }
            }
            
            return dto;
        }
    }
