package com.group6.Rental_Car.services.rentalorderdetail;

import com.group6.Rental_Car.dtos.rentalorderdetail.RentalOrderDetailCreateRequest;
import com.group6.Rental_Car.dtos.rentalorderdetail.RentalOrderDetailResponse;
import com.group6.Rental_Car.dtos.rentalorderdetail.RentalOrderDetailUpdateRequest;
import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.entities.RentalOrderDetail;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.RentalOrderDetailRepository;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RentalOrderDetailServiceImpl implements RentalOrderDetailService {

    private final RentalOrderDetailRepository detailRepository;
    private final RentalOrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    public RentalOrderDetailResponse create(RentalOrderDetailCreateRequest req) {
        if (req.getOrderId() == null) throw new BadRequestException("orderId is required");
        if (req.getVehicleId() == null) throw new BadRequestException("vehicleId is required");
        if (req.getStartTime() == null) throw new BadRequestException("startTime is required");
        if (req.getEndTime() == null) throw new BadRequestException("endTime is required");
        if (req.getStartTime().isAfter(req.getEndTime())) {
            throw new BadRequestException("startTime must be before endTime");
        }

        RentalOrder order = orderRepository.findById(req.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + req.getOrderId()));
        Vehicle vehicle = vehicleRepository.findById(req.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + req.getVehicleId()));

        RentalOrderDetail d = RentalOrderDetail.builder()
                .order(order)
                .vehicle(vehicle)
                .type(req.getType() == null ? "RENTAL" : req.getType())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .price(req.getPrice())
                .description(req.getDescription())
                .status(req.getStatus())
                .build();

        d = detailRepository.save(d);
        return toResponse(d);
    }

    @Override
    public RentalOrderDetailResponse update(Long detailId, RentalOrderDetailUpdateRequest req) {
        RentalOrderDetail d = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Detail not found: " + detailId));

        if (req.getOrderId() != null) {
            RentalOrder order = orderRepository.findById(req.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + req.getOrderId()));
            d.setOrder(order);
        }
        if (req.getVehicleId() != null) {
            Vehicle v = vehicleRepository.findById(req.getVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + req.getVehicleId()));
            d.setVehicle(v);
        }
        if (req.getType() != null) d.setType(req.getType());
        if (req.getStartTime() != null) d.setStartTime(req.getStartTime());
        if (req.getEndTime() != null) d.setEndTime(req.getEndTime());
        if (d.getStartTime() != null && d.getEndTime() != null
                && d.getStartTime().isAfter(d.getEndTime())) {
            throw new BadRequestException("startTime must be before endTime");
        }
        if (req.getPrice() != null) d.setPrice(req.getPrice());
        if (req.getDescription() != null) d.setDescription(req.getDescription());
        if (req.getStatus() != null) d.setStatus(req.getStatus());

        d = detailRepository.save(d);
        return toResponse(d);
    }

    @Override
    public void delete(Long detailId) {
        RentalOrderDetail d = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Detail not found: " + detailId));
        detailRepository.delete(d);
    }

    @Override
    public RentalOrderDetailResponse getById(Long detailId) {
        return detailRepository.findById(detailId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Detail not found: " + detailId));
    }

    @Override
    public List<RentalOrderDetailResponse> listAll() {
        return detailRepository.findAllByOrderByStartTimeDesc().stream().map(this::toResponse).toList();
    }

    @Override
    public List<RentalOrderDetailResponse> listByOrder(UUID orderId) {
        return detailRepository.findAllByOrder_OrderIdOrderByStartTimeDesc(orderId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<RentalOrderDetailResponse> listByVehicle(Long vehicleId) {
        return detailRepository.findAllByVehicle_VehicleIdOrderByStartTimeDesc(vehicleId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<RentalOrderDetailResponse> listByStatus(String status) {
        return detailRepository.findAllByStatusOrderByStartTimeDesc(status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<RentalOrderDetailResponse> listByType(String type) {
        return detailRepository.findAllByTypeOrderByStartTimeDesc(type)
                .stream().map(this::toResponse).toList();
    }

    private RentalOrderDetailResponse toResponse(RentalOrderDetail d) {
        return RentalOrderDetailResponse.builder()
                .detailId(d.getDetailId())
                .orderId(d.getOrder() != null ? d.getOrder().getOrderId() : null)
                .orderStatus(d.getOrder() != null ? d.getOrder().getStatus() : null)
                .vehicleId(d.getVehicle() != null ? d.getVehicle().getVehicleId() : null)
                .plateNumber(d.getVehicle() != null ? d.getVehicle().getPlateNumber() : null)
                .vehicleName(d.getVehicle() != null ? d.getVehicle().getVehicleName() : null)
                .type(d.getType())
                .startTime(d.getStartTime())
                .endTime(d.getEndTime())
                .price(d.getPrice())
                .description(d.getDescription())
                .status(d.getStatus())
                .build();
    }
}
