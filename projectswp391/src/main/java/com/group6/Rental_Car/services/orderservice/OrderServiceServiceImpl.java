package com.group6.Rental_Car.services.orderservice;

import com.group6.Rental_Car.dtos.orderservice.OrderServiceCreateRequest;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        //  1. LƯU VÀO BẢNG ORDERSERVICE (bảng chính để quản lý service)
        OrderService service = OrderService.builder()
                .serviceType(request.getServiceType().toUpperCase())
                .description(Optional.ofNullable(request.getDescription())
                        .orElse("Phí dịch vụ " + request.getServiceType()))
                .cost(request.getCost())
                .build();
        OrderService savedService = orderServiceRepository.save(service);

        //  2. LƯU VÀO BẢNG RENTAL_ORDER_DETAIL (để getDetailsByOrder và payment có thể lấy được)
        RentalOrderDetail serviceDetail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(vehicle)
                .type("SERVICE_" + request.getServiceType().toUpperCase())
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .price(request.getCost())
                .status("PENDING")
                .description(Optional.ofNullable(request.getDescription())
                        .orElse("Phí dịch vụ " + request.getServiceType()))
                .build();
        rentalOrderDetailRepository.save(serviceDetail);

        //  3. Cập nhật tổng tiền đơn thuê
        order.setTotalPrice(order.getTotalPrice().add(request.getCost()));
        rentalOrderRepository.save(order);

        //  4. Tạo response từ OrderService (bảng chính)
        OrderServiceResponse response = new OrderServiceResponse();
        response.setServiceId(savedService.getServiceId());
        response.setServiceType(request.getServiceType());
        response.setDescription(savedService.getDescription());
        response.setCost(request.getCost());

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