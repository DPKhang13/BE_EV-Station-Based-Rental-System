package com.group6.Rental_Car.services.orderservice;

import com.group6.Rental_Car.dtos.orderservice.OrderServiceRequest;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceServiceImpl implements OrderServiceService {

    private final OrderServiceRepository orderServiceRepository;
    private final RentalOrderRepository rentalOrderRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final RentalStationRepository stationRepository;
    private final ModelMapper modelMapper;

    @Override
    public OrderServiceResponse createService(OrderServiceRequest request) {
        RentalOrder order = rentalOrderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe"));

        RentalOrderDetail detail = null;
        if (request.getDetailId() != null) {
            detail = rentalOrderDetailRepository.findById(request.getDetailId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi tiết đơn thuê"));
        }

        User staff = null;
        if (request.getPerformedById() != null) {
            staff = userRepository.findById(request.getPerformedById())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên thực hiện"));
        }

        RentalStation station = null;
        if (request.getStationId() != null) {
            station = stationRepository.findById(request.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạm"));
        }

        OrderService service = OrderService.builder()
                .order(order)
                .vehicle(vehicle)
                .detail(detail)
                .serviceType(request.getServiceType())
                .description(request.getDescription())
                .cost(request.getCost())
                .performedBy(staff)
                .station(station)
                .occurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : java.time.LocalDateTime.now())
                .resolvedAt(request.getResolvedAt())
                .status(request.getStatus() != null ? request.getStatus() : "pending")
                .note(request.getNote())
                .build();

        OrderService saved = orderServiceRepository.save(service);
        return toResponse(saved);
    }

    @Override
    public OrderServiceResponse updateService(Long serviceId, OrderServiceRequest request) {
        OrderService existing = orderServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ với ID: " + serviceId));

        existing.setServiceType(request.getServiceType());
        existing.setDescription(request.getDescription());
        existing.setCost(request.getCost());
        existing.setResolvedAt(request.getResolvedAt());
        existing.setStatus(request.getStatus());
        existing.setNote(request.getNote());

        OrderService updated = orderServiceRepository.save(existing);
        return toResponse(updated);
    }

    @Override
    public void deleteService(Long serviceId) {
        if (!orderServiceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Không tìm thấy dịch vụ để xóa");
        }
        orderServiceRepository.deleteById(serviceId);
    }

    @Override
    public List<OrderServiceResponse> getServicesByOrder(UUID orderId) {
        return orderServiceRepository.findByOrder_OrderId(orderId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OrderServiceResponse> getServicesByVehicle(Long vehicleId) {
        return orderServiceRepository.findByVehicle_VehicleId(vehicleId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OrderServiceResponse> getServicesByStation(Integer stationId) {
        return orderServiceRepository.findByStation_StationId(stationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OrderServiceResponse> getServicesByStatus(String status) {
        return orderServiceRepository.findByStatusIgnoreCase(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private OrderServiceResponse toResponse(OrderService entity) {
        OrderServiceResponse dto = modelMapper.map(entity, OrderServiceResponse.class);

        dto.setOrderId(entity.getOrder() != null ? entity.getOrder().getOrderId() : null);
        dto.setVehicleId(entity.getVehicle() != null ? entity.getVehicle().getVehicleId() : null);
        dto.setDetailId(entity.getDetail() != null ? entity.getDetail().getDetailId() : null);
        dto.setPerformedByName(entity.getPerformedBy() != null ? entity.getPerformedBy().getFullName() : null);
        dto.setStationName(entity.getStation() != null ? entity.getStation().getName() : null);

        return dto;
    }
}
