package com.group6.Rental_Car.services.scheduler;

import com.group6.Rental_Car.entities.Payment;
import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.entities.RentalOrderDetail;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleTimeline;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.repositories.PaymentRepository;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import com.group6.Rental_Car.repositories.VehicleTimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMaintenanceServiceImpl implements OrderMaintenanceService {

    private final RentalOrderRepository rentalOrderRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleTimelineRepository vehicleTimelineRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public void autoCancelPendingOrders() {
        List<RentalOrder> pendingOrders = rentalOrderRepository.findByStatus("PENDING");

        for (RentalOrder order : pendingOrders) {
            LocalDateTime created = order.getCreatedAt();
            if (created == null) continue;

            Duration duration = Duration.between(created, LocalDateTime.now());
            if (duration.toMinutes() >= 30) {
                
                // Kiểm tra xem order có payment PENDING không
                // Nếu có payment PENDING (đang chờ thanh toán), không hủy order
                List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(order.getOrderId());
                boolean hasPendingPayment = payments.stream()
                        .anyMatch(p -> p.getStatus() == PaymentStatus.PENDING);
                
                if (hasPendingPayment) {
                    log.debug("Order {} có payment PENDING, bỏ qua auto-cancel", order.getOrderId());
                    continue;
                }
                
                // Chỉ hủy order nếu không có payment hoặc tất cả payment đã FAILED
                boolean hasAnySuccessPayment = payments.stream()
                        .anyMatch(p -> p.getStatus() == PaymentStatus.SUCCESS);
                
                if (hasAnySuccessPayment) {
                    log.debug("Order {} đã có payment SUCCESS, bỏ qua auto-cancel", order.getOrderId());
                    continue;
                }

                //  Cập nhật trạng thái đơn
                order.setStatus("PAYMENT_FAILED");

                //  Tìm xe trong chi tiết chính
                Vehicle vehicle = order.getDetails().stream()
                        .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                        .map(RentalOrderDetail::getVehicle)
                        .findFirst()
                        .orElse(null);

                if (vehicle != null) {
                    //  Giải phóng xe
                    vehicle.setStatus("AVAILABLE");
                    vehicleRepository.save(vehicle);

                    //  Xóa toàn bộ timeline liên quan đến xe này
                    List<VehicleTimeline> timelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicle.getVehicleId());
                    if (!timelines.isEmpty()) {
                        vehicleTimelineRepository.deleteAll(timelines);
                        log.info(" Xóa {} timeline của xe {} do order {} bị hủy",
                                timelines.size(), vehicle.getVehicleId(), order.getOrderId());
                    }
                }

                rentalOrderRepository.save(order);
                log.info("Auto-cancel order {} — quá 30 phút chưa thanh toán và không có payment PENDING", order.getOrderId());
            }
        }
    }
}