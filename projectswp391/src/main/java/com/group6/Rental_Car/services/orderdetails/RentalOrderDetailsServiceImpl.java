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

            existing.setOrder(order);
            existing.setVehicle(vehicle);
            existing.setType(request.getType().trim().toUpperCase());
            existing.setStartTime(request.getStartTime());
            existing.setEndTime(request.getEndTime());
            existing.setPrice(request.getPrice());
            existing.setDescription(request.getDescription());

            return toResponse(rentalOrderDetailRepository.save(existing));
        }

        // =====================================================
        //  DELETE DETAIL
        // =====================================================
        @Override
        public void deleteDetail(Long detailId) {
            if (!rentalOrderDetailRepository.existsById(detailId)) {
                throw new ResourceNotFoundException("Chi tiết thuê không tồn tại");
            }
            rentalOrderDetailRepository.deleteById(detailId);
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
            
            // Show RENTAL khi: PENDING (chưa thanh toán), CREATED, BOOKED
            // Show TẤT CẢ khi: FAILED (để hiển thị đầy đủ thông tin đơn đã hủy)
            boolean showOnlyRental =
                    status.equals("PENDING") ||
                    status.equals("CREATED") ||
                    status.equals("BOOKED");
            boolean showAll = status.equals("FAILED");

            List<RentalOrderDetail> raw = rentalOrderDetailRepository.findByOrder_OrderId(orderId);

            // Lấy số tiền còn lại chưa thanh toán từ Payment
            // Logic: Kiểm tra FULL_PAYMENT SUCCESS trước (có thể có remainingAmount > 0 nếu thêm dịch vụ)
            // Sau đó mới kiểm tra DEPOSIT SUCCESS
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
                // Kiểm tra FINAL_PAYMENT (type 2) SUCCESS → đã thanh toán hết
                boolean hasFinalPaymentSuccess = payments.stream()
                        .anyMatch(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.SUCCESS);
                if (hasFinalPaymentSuccess) {
                    remainingAmount = BigDecimal.ZERO;
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

            List<OrderDetailResponse> details = raw.stream()
                    .filter(d -> {
                        // Nếu FAILED, show tất cả detail
                        if (showAll) return true;
                        
                        String type = safeType(d);
                        if (showOnlyRental) return type.equals("RENTAL");
                        return !type.equals("RENTAL");
                    })
                    .map(d -> {
                        OrderDetailResponse dto = toResponse(d, order);
                        
                        // Chỉ set payment method cho các detail có payment tương ứng
                        // SERVICE detail không có payment riêng → không set method
                        String detailType = safeType(d);
                        String methodPayment = null;
                        
                        if (!"SERVICE".equals(detailType)) {
                            // Tìm payment method tương ứng với detail type
                            methodPayment = payments.stream()
                                    .sorted(Comparator.comparing(Payment::getPaymentId))
                                    .filter(p -> {
                                        // Map detail type với payment type
                                        if ("DEPOSIT".equals(detailType) && p.getPaymentType() == 1) return true;
                                        if ("PICKUP".equals(detailType) && p.getPaymentType() == 2) return true;
                                        if ("FULL_PAYMENT".equals(detailType) && p.getPaymentType() == 3) return true;
                                        return false;
                                    })
                                    .map(Payment::getMethod)
                                    .map(mth -> mth != null && mth.equalsIgnoreCase("CASH") ? "CASH" : mth)
                                    .findFirst()
                                    .orElse(null);
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
