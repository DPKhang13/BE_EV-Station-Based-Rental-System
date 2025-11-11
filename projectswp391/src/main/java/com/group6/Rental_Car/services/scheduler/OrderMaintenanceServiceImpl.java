package com.group6.Rental_Car.services.scheduler;
import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.entities.RentalOrderDetail;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
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

    @Override
    @Transactional
    public void autoCancelPendingOrders() {
        List<RentalOrder> pendingOrders = rentalOrderRepository.findByStatus("PENDING");

        for (RentalOrder order : pendingOrders) {
            LocalDateTime created = order.getCreatedAt();
            if (created == null) continue;

            Duration duration = Duration.between(created, LocalDateTime.now());
            if (duration.toMinutes() >= 10) {
                order.setStatus("PAYMENT_FAILED");

                Vehicle vehicle = order.getDetails().stream()
                        .filter(d -> "RENTAL".equalsIgnoreCase(d.getType())) // chi tiết chính
                        .map(RentalOrderDetail::getVehicle)
                        .findFirst()
                        .orElse(null);
                if (vehicle != null) {
                    vehicle.setStatus("AVAILABLE");
                    vehicleRepository.save(vehicle);
                }

                rentalOrderRepository.save(order);
                log.info(" Auto-cancel order {} — quá 10 phút chưa thanh toán", order.getOrderId());
            }
        }
    }
}