package com.group6.Rental_Car.services.orderservice;

import com.group6.Rental_Car.dtos.orderservice.OrderServiceCreateRequest;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;
import com.group6.Rental_Car.dtos.orderservice.OrderServiceUpdateRequest;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.OrderServiceRepository;
import com.group6.Rental_Car.repositories.RentalOrderDetailRepository;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.group6.Rental_Car.utils.ValidationUtil.*;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

@Service
@RequiredArgsConstructor
public class OrderServiceManagerImpl implements OrderServiceManager {

    private final OrderServiceRepository orderServiceRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalOrderRepository rentalOrderRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;

    @Override
    public OrderServiceResponse create(OrderServiceCreateRequest req) {
        Long vehicleId = requireNonNull(req.getVehicleId(), "vehicleId");
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        String type = defaultIfBlank(req.getServiceType(), "MAINTENANCE");
        String status = defaultIfBlank(req.getStatus(), "pending");
        String description = trim(req.getDescription());
        ensureMaxLength(description, 4000, "description");

        BigDecimal cost = req.getCost();
        if (cost != null) ensureNonNegative(cost, "cost");

        // Lấy stationId từ quan hệ ManyToOne
        Integer stationId = (req.getStationId() != null)
                ? req.getStationId()
                : Optional.ofNullable(vehicle.getRentalStation())
                .map(RentalStation::getStationId)
                .orElse(null);

        OrderService os = new OrderService();
        os.setVehicle(vehicle);
        os.setStationId(stationId);
        os.setServiceType(type);
        os.setStatus(status);
        os.setDescription(description);
        os.setCost(cost);
        os.setNote(req.getNote());
        os.setOccurredAt(req.getOccurredAt() != null ? req.getOccurredAt() : LocalDateTime.now());

        if (req.getOrderId() != null) {
            UUID oid = req.getOrderId();
            RentalOrder order = rentalOrderRepository.findById(oid)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + oid));
            os.setOrder(order);
        }
        if (req.getDetailId() != null) {
            Long did = req.getDetailId();
            RentalOrderDetail detail = rentalOrderDetailRepository.findById(did)
                    .orElseThrow(() -> new ResourceNotFoundException("Order detail not found: " + did));
            os.setDetail(detail);
        }

        os = orderServiceRepository.save(os);
        return toResponse(os);
    }

    @Override
    public OrderServiceResponse update(Integer serviceId, OrderServiceUpdateRequest req) {
        OrderService os = orderServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderService not found: " + serviceId));

        if (req.getVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(req.getVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + req.getVehicleId()));
            os.setVehicle(vehicle);

            Integer stationId = Optional.ofNullable(vehicle.getRentalStation())
                    .map(RentalStation::getStationId)
                    .orElse(null);
            os.setStationId(stationId);
        }

        if (req.getStationId() != null) os.setStationId(req.getStationId());
        if (req.getServiceType() != null) os.setServiceType(req.getServiceType());

        if (req.getDescription() != null) {
            String d = trim(req.getDescription());
            ensureMaxLength(d, 4000, "description");
            os.setDescription(d);
        }

        if (req.getCost() != null) {
            ensureNonNegative(req.getCost(), "cost");
            os.setCost(req.getCost());
        }

        if (req.getOccurredAt() != null) os.setOccurredAt(req.getOccurredAt());
        if (req.getResolvedAt() != null) os.setResolvedAt(req.getResolvedAt());

        if (req.getStatus() != null) {
            boolean becomingDone = !"done".equalsIgnoreCase(os.getStatus())
                    && "done".equalsIgnoreCase(req.getStatus());
            os.setStatus(req.getStatus());
            if (becomingDone && os.getResolvedAt() == null) {
                os.setResolvedAt(LocalDateTime.now());
            }
        }

        if (req.getNote() != null) os.setNote(req.getNote());

        if (req.getOrderId() != null) {
            RentalOrder order = rentalOrderRepository.findById(req.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + req.getOrderId()));
            os.setOrder(order);
        }

        if (req.getDetailId() != null) {
            RentalOrderDetail detail = rentalOrderDetailRepository.findById(req.getDetailId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order detail not found: " + req.getDetailId()));
            os.setDetail(detail);
        }

        os = orderServiceRepository.save(os);
        return toResponse(os);
    }

    @Override
    public void delete(Integer serviceId) {
        OrderService os = orderServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderService not found: " + serviceId));
        orderServiceRepository.delete(os);
    }

    @Override
    public OrderServiceResponse getById(Integer serviceId) {
        return orderServiceRepository.findById(serviceId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("OrderService not found: " + serviceId));
    }

    @Override
    public List<OrderServiceResponse> listAll() {
        return orderServiceRepository.findAllByOrderByOccurredAtDesc()
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<OrderServiceResponse> listByType(String type) {
        return orderServiceRepository.findByServiceTypeOrderByOccurredAtDesc(type)
                .stream().map(this::toResponse).toList();
    }

    private OrderServiceResponse toResponse(OrderService i) {
        OrderServiceResponse dto = new OrderServiceResponse();
        dto.setServiceId(i.getServiceId());
        dto.setVehicleId(i.getVehicle() != null ? i.getVehicle().getVehicleId() : null);
        dto.setPlateNumber(i.getVehicle() != null ? i.getVehicle().getPlateNumber() : null);
        dto.setStationId(i.getStationId());
        dto.setServiceType(i.getServiceType());
        dto.setDescription(i.getDescription());
        dto.setCost(i.getCost());
        dto.setOccurredAt(i.getOccurredAt());
        dto.setResolvedAt(i.getResolvedAt());
        dto.setStatus(i.getStatus());
        dto.setNote(i.getNote());
        dto.setOrderId(i.getOrder() != null ? i.getOrder().getOrderId() : null);
        dto.setDetailId(i.getDetail() != null ? i.getDetail().getDetailId() : null);
        return dto;
    }
}
