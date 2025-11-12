package com.group6.Rental_Car.services.orderdetails;

import com.group6.Rental_Car.dtos.orderdetail.OrderDetailCreateRequest;
import com.group6.Rental_Car.dtos.orderdetail.OrderDetailResponse;
import com.group6.Rental_Car.entities.OrderService;
import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.entities.RentalOrderDetail;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.OrderServiceRepository;
import com.group6.Rental_Car.repositories.RentalOrderDetailRepository;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final OrderServiceRepository orderServiceRepository;

    @Override
    public OrderDetailResponse createDetail(OrderDetailCreateRequest request) {
        RentalOrder order = rentalOrderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe"));

        RentalOrderDetail detail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(vehicle)
                .type(request.getType())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .price(request.getPrice())
                .description(request.getDescription())
                .status("PENDING")
                .build();

        RentalOrderDetail saved = rentalOrderDetailRepository.save(detail);
        return toResponse(saved);
    }

    @Override
    public OrderDetailResponse updateDetail(Long detailId, OrderDetailCreateRequest request) {
        RentalOrderDetail existing = rentalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi tiết thuê với ID: " + detailId));

        RentalOrder order = rentalOrderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe"));

        existing.setOrder(order);
        existing.setVehicle(vehicle);
        existing.setType(request.getType());
        existing.setStartTime(request.getStartTime());
        existing.setEndTime(request.getEndTime());
        existing.setPrice(request.getPrice());
        existing.setDescription(request.getDescription());

        RentalOrderDetail saved = rentalOrderDetailRepository.save(existing);
        return toResponse(saved);
    }

    @Override
    public void deleteDetail(Long detailId) {
        if (!rentalOrderDetailRepository.existsById(detailId)) {
            throw new ResourceNotFoundException("Chi tiết thuê không tồn tại");
        }
        rentalOrderDetailRepository.deleteById(detailId);
    }

    @Override
    public List<OrderDetailResponse> getDetailsByOrder(UUID orderId) {
        List<OrderDetailResponse> details = rentalOrderDetailRepository.findByOrder_OrderId(orderId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // Gộp thêm các OrderService của order đó
        List<OrderService> services = orderServiceRepository.findByOrder_OrderId(orderId);
        for (OrderService s : services) {
            OrderDetailResponse dto = new OrderDetailResponse();
            dto.setDetailId(s.getServiceId());
            dto.setOrderId(orderId);
            dto.setVehicleId(s.getVehicle() != null ? s.getVehicle().getVehicleId() : null);
            dto.setType("SERVICE");
            dto.setStartTime(s.getOccurredAt());
            dto.setEndTime(s.getResolvedAt());
            dto.setPrice(s.getCost());
            dto.setStatus(s.getStatus());
            dto.setDescription(s.getServiceType() + (s.getDescription() != null ? " - " + s.getDescription() : ""));
            details.add(dto);
        }

        return details;
    }

    @Override
    public List<OrderDetailResponse> getDetailsByVehicle(Long vehicleId) {
        return rentalOrderDetailRepository.findByVehicle_VehicleId(vehicleId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OrderDetailResponse> getActiveDetailsByVehicle(Long vehicleId) {
        List<String> activeStatuses = List.of("PENDING", "SUCCESS", "FAILED");
        return rentalOrderDetailRepository.findByVehicle_VehicleIdAndStatusIn(vehicleId, activeStatuses)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OrderDetailResponse> getActiveDetailsByOrder(UUID orderId) {
        List<String> activeStatuses = List.of("PENDING", "SUCCESS", "FAILED");
        return rentalOrderDetailRepository.findByOrder_OrderIdAndStatusIn(orderId, activeStatuses)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ==============================
    // Private helper
    // ==============================
    private OrderDetailResponse toResponse(RentalOrderDetail detail) {
        OrderDetailResponse dto = modelMapper.map(detail, OrderDetailResponse.class);
        dto.setOrderId(detail.getOrder().getOrderId());
        dto.setVehicleId(detail.getVehicle().getVehicleId());
        return dto;
    }
}
