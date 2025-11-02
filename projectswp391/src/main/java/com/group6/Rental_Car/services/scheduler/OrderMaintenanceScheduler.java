package com.group6.Rental_Car.services.scheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMaintenanceScheduler {

    private final OrderMaintenanceService orderMaintenanceService;

    @Scheduled(fixedRate = 60000) // Mỗi 1 phút
    public void scheduleAutoCancel() {
        log.debug(" Kiểm tra đơn hàng quá hạn thanh toán...");
        orderMaintenanceService.autoCancelPendingOrders();
    }
}