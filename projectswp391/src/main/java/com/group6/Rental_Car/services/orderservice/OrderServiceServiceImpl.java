    package com.group6.Rental_Car.services.orderservice;

    import com.group6.Rental_Car.dtos.orderservice.OrderServiceCreateRequest;
    import com.group6.Rental_Car.dtos.orderservice.OrderServiceResponse;
    import com.group6.Rental_Car.entities.*;
    import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
    import com.group6.Rental_Car.repositories.*;
    import com.group6.Rental_Car.utils.JwtUserDetails;
    import lombok.RequiredArgsConstructor;
    import org.modelmapper.ModelMapper;
    import org.springframework.security.core.context.SecurityContextHolder;
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
        private final VehicleRepository vehicleRepository;
        private final UserRepository userRepository;
        private final RentalStationRepository stationRepository;
        private final ModelMapper modelMapper;

        // ===============================
        // 🧩 TẠO DỊCH VỤ LIÊN QUAN ĐẾN ORDER
        // ===============================
        @Override
        public OrderServiceResponse createService(OrderServiceCreateRequest request) {
            //  1. Lấy đơn thuê
            RentalOrder order = rentalOrderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê"));

            //  2. Lấy xe chính của đơn
            Vehicle vehicle = order.getDetails().stream()
                    .map(RentalOrderDetail::getVehicle)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe thuộc đơn thuê"));

            //  3. Lấy thông tin trạm (nếu có)
            RentalStation station = vehicle.getRentalStation();

            //  4. Lấy nhân viên đang đăng nhập (nếu có)
            User performedBy = null;
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof JwtUserDetails jwt) {
                performedBy = userRepository.findById(jwt.getUserId()).orElse(null);
            }

            //  5. Tạo chi tiết dịch vụ (lưu thẳng vào bảng rental_order_detail)
            RentalOrderDetail serviceDetail = RentalOrderDetail.builder()
                    .order(order)
                    .vehicle(vehicle)
                    .type("SERVICE_" + request.getServiceType().toUpperCase())
                    .startTime(LocalDateTime.now())
                    .endTime(LocalDateTime.now()) // chưa hoàn tất, chưa có endTime
                    .price(request.getCost())
                    .status("PENDING")
                    .description(
                            Optional.ofNullable(request.getDescription())
                                    .orElse("Phí dịch vụ " + request.getServiceType())
                    )
                    .build();
            rentalOrderDetailRepository.save(serviceDetail);

            //  6. Cập nhật tổng tiền đơn thuê
            order.setTotalPrice(order.getTotalPrice().add(request.getCost()));
            rentalOrderRepository.save(order);

            // 7. Chuẩn bị response
            OrderServiceResponse response = new OrderServiceResponse();
            response.setOrderId(order.getOrderId());
            response.setDetailId(serviceDetail.getDetailId());
            response.setVehicleId(vehicle.getVehicleId());
            response.setServiceType(request.getServiceType());
            response.setDescription(serviceDetail.getDescription());
            response.setCost(request.getCost());
            response.setStatus("PENDING");
            response.setOccurredAt(serviceDetail.getStartTime());
            response.setResolvedAt(serviceDetail.getEndTime());
            response.setStationName(station != null ? station.getName() : null);
            response.setPerformedByName(performedBy != null ? performedBy.getFullName() : null);
            response.setNote(null);

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

        // ===============================
        // 🔁 HELPER
        // ===============================
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
