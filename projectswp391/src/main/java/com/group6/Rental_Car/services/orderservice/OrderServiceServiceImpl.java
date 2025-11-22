package com.group6.Rental_Car.services.orderservice;

import com.group6.Rental_Car.dtos.orderservice.OrderServiceCreateRequest;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;
import com.group6.Rental_Car.dtos.orderservice.ServicePriceCreateRequest;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceServiceImpl implements OrderServiceService {

    private final OrderServiceRepository orderServiceRepository;
    private final RentalOrderRepository rentalOrderRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;
    private final PaymentRepository paymentRepository;

    // ===============================
    //  TẠO DỊCH VỤ LIÊN QUAN ĐẾN ORDER
    // ===============================
    @Override
    @Transactional
    public OrderServiceResponse createService(OrderServiceCreateRequest request) {
        // 1⃣ Lấy đơn thuê
        RentalOrder order = rentalOrderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        //  Lấy xe
        Vehicle vehicle = order.getDetails().stream()
                .map(RentalOrderDetail::getVehicle)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe trong đơn"));

        //  CHỈ TẠO RENTAL_ORDER_DETAIL (không tạo OrderService entity)
        String description = Optional.ofNullable(request.getDescription())
                .orElse("Phí dịch vụ " + request.getServiceType());
        
        RentalOrderDetail serviceDetail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(vehicle)
                .type("SERVICE")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .price(request.getCost())
                .status("PENDING")
                .description(description)
                .build();
        RentalOrderDetail savedDetail = rentalOrderDetailRepository.save(serviceDetail);

        //  Cập nhật tổng tiền đơn thuê
        BigDecimal currentTotal = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
        order.setTotalPrice(currentTotal.add(request.getCost()));
        rentalOrderRepository.save(order);

        //  Cập nhật remainingAmount của payment nếu có
        //  - Nếu có payment type 1 (deposit) SUCCESS → cập nhật remainingAmount = remainingAmount + giá dịch vụ
        //  - Nếu có payment type 3 (full payment) SUCCESS → cập nhật remainingAmount = 0 + giá dịch vụ (cần thanh toán thêm)
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
            deposit.setRemainingAmount(currentRemaining.add(request.getCost()));
            paymentRepository.save(deposit);
            System.out.println("✅ [createService] Đã cập nhật remainingAmount cho deposit payment: " + 
                    currentRemaining + " + " + request.getCost() + " = " + deposit.getRemainingAmount());
        } else {
            // Tìm payment type 3 (full payment) SUCCESS
            Optional<Payment> fullPayment = payments.stream()
                    .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();
            
            if (fullPayment.isPresent()) {
                Payment full = fullPayment.get();
                // Type 3 đã thanh toán hết, giờ cần thanh toán thêm dịch vụ
                // Cộng thêm vào remainingAmount hiện tại (có thể đã có dịch vụ trước đó)
                BigDecimal currentRemaining = full.getRemainingAmount() != null 
                        ? full.getRemainingAmount() 
                        : BigDecimal.ZERO;
                full.setRemainingAmount(currentRemaining.add(request.getCost()));
                paymentRepository.save(full);
                System.out.println("✅ [createService] Đã cập nhật remainingAmount cho full payment: " + 
                        currentRemaining + " + " + request.getCost() + " = " + full.getRemainingAmount());
            }
        }

        //  Tạo response từ RentalOrderDetail
        OrderServiceResponse response = new OrderServiceResponse();
        response.setServiceId(savedDetail.getDetailId()); // Dùng detailId thay vì serviceId
        response.setServiceType(request.getServiceType());
        response.setDescription(description);
        response.setCost(request.getCost());

        return response;
    }

    // ===============================
    //  TẠO DỊCH VỤ CHUNG (BẢNG GIÁ) - KHÔNG CẦN ORDERID
    // ===============================
    @Override
    @Transactional
    public OrderServiceResponse createServicePrice(ServicePriceCreateRequest request) {
        // Validate
        if (request.getServiceType() == null || request.getServiceType().trim().isEmpty()) {
            throw new BadRequestException("Service type là bắt buộc");
        }
        if (request.getCost() == null || request.getCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Cost phải lớn hơn 0");
        }

        // Tạo OrderService entity (dịch vụ chung trong bảng giá)
        OrderService service = OrderService.builder()
                .serviceType(request.getServiceType().toUpperCase())
                .description(Optional.ofNullable(request.getDescription())
                        .orElse("Phí dịch vụ " + request.getServiceType()))
                .cost(request.getCost())
                .build();
        
        OrderService savedService = orderServiceRepository.save(service);

        // Tạo response
        OrderServiceResponse response = new OrderServiceResponse();
        response.setServiceId(savedService.getServiceId());
        response.setServiceType(savedService.getServiceType());
        response.setDescription(savedService.getDescription());
        response.setCost(savedService.getCost());

        return response;
    }

    @Override
    public OrderServiceResponse updateService(Long serviceId, OrderServiceCreateRequest request) {
        OrderService existing = orderServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ với ID: " + serviceId));

        existing.setServiceType(request.getServiceType());
        existing.setDescription(request.getDescription());
        existing.setCost(request.getCost());

        OrderService updated = orderServiceRepository.save(existing);
        return toResponse(updated);
    }

    // ===============================
    // 🗑️ XÓA DỊCH VỤ
    // ===============================
    @Override
    public void deleteService(Long serviceId) {
        if (!orderServiceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Không tìm thấy dịch vụ để xóa");
        }
        orderServiceRepository.deleteById(serviceId);
    }

    // ===============================
    // 📜 LẤY DANH SÁCH DỊCH VỤ THEO ORDER
    // ===============================
    @Override
    public List<OrderServiceResponse> getServicesByOrder(UUID orderId) {
        return orderServiceRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OrderServiceResponse> getServicesByVehicle(Long vehicleId) {
        return orderServiceRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OrderServiceResponse> getServicesByStation(Integer stationId) {
        return orderServiceRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OrderServiceResponse> getServicesByStatus(String status) {
        return orderServiceRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ===============================
    // 💰 BẢNG GIÁ DỊCH VỤ
    // ===============================
    @Override
    public List<OrderServiceResponse> getPriceList() {
        // Lấy tất cả các dịch vụ, sắp xếp theo serviceType
        return orderServiceRepository.findAll()
                .stream()
                .sorted((s1, s2) -> {
                    // Sắp xếp theo serviceType
                    int typeCompare = s1.getServiceType().compareToIgnoreCase(s2.getServiceType());
                    if (typeCompare != 0) return typeCompare;
                    // Nếu cùng type, sắp xếp theo cost
                    return s1.getCost().compareTo(s2.getCost());
                })
                .map(this::toResponse)
                .toList();
    }

    // ===============================
    // 🔁 HELPER
    // ===============================
    private OrderServiceResponse toResponse(OrderService entity) {
        OrderServiceResponse dto = new OrderServiceResponse();
        dto.setServiceId(entity.getServiceId());
        dto.setServiceType(entity.getServiceType());
        dto.setDescription(entity.getDescription());
        dto.setCost(entity.getCost());
        return dto;
    }
}