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
            String methodPayment = paymentRepository
                    .findByRentalOrder_OrderId(order.getOrderId()).stream()
                    .sorted(Comparator.comparing(Payment::getPaymentId))
                    .map(Payment::getMethod)
                    .map(mth -> mth.equalsIgnoreCase("CASH") ? "CASH" : "captureWallet")
                    .findFirst()
                    .orElse("captureWallet");
            // Show RENTAL khi: PENDING (chưa thanh toán), CREATED, BOOKED
            boolean showOnlyRental =
                    status.equals("PENDING") ||
                    status.equals("CREATED") ||
                    status.equals("BOOKED");

            List<RentalOrderDetail> raw = rentalOrderDetailRepository.findByOrder_OrderId(orderId);

            // Lấy số tiền còn lại chưa thanh toán từ Payment
            BigDecimal remainingAmount = Optional.ofNullable(order.getPayments())
                    .orElse(List.of()).stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst()
                    .map(p -> Optional.ofNullable(p.getRemainingAmount()).orElse(BigDecimal.ZERO))
                    .orElse(BigDecimal.ZERO);

            List<OrderDetailResponse> details = raw.stream()
                    .filter(d -> {
                        String type = safeType(d);

                        if (showOnlyRental) return type.equals("RENTAL");
                        return !type.equals("RENTAL");
                    })
                    .map(d -> {
                        OrderDetailResponse dto = toResponse(d, order);
                        dto.setMethodPayment(methodPayment);
                        dto.setRemainingAmount(remainingAmount);
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
