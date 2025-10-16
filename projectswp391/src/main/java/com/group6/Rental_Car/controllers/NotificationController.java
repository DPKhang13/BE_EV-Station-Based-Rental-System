package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.maintenance.MaintenanceCreateRequest;
import com.group6.Rental_Car.dtos.maintenance.MaintenanceResponse;
import com.group6.Rental_Car.dtos.notification.NotificationCreateRequest;
import com.group6.Rental_Car.dtos.notification.NotificationResponse;
import com.group6.Rental_Car.dtos.notification.NotificationUpdateRequest;
import com.group6.Rental_Car.services.notification.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Notification API", description = "User nhận thông báo từ dịch vụ")
@RequestMapping(name = "/api/notification")
public class NotificationController {
    private final NotificationService notificationService;
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    @GetMapping("/create")
    public ResponseEntity<?> create(@RequestBody NotificationCreateRequest req) {
        return ResponseEntity.ok(notificationService.create(req));
    }

    @PostMapping("/update/{notification_id}")
    public ResponseEntity<?> update(@PathVariable Integer notificationId, @RequestBody NotificationUpdateRequest req) {
        return ResponseEntity.ok(notificationService.update(notificationId, req));
    }

    @DeleteMapping("/delete/{notificationId}")
    public ResponseEntity<?> delete(@PathVariable Integer notificationId) {
        notificationService.delete(notificationId);
        return ResponseEntity.ok("Deleted maintenance successfully");
    }

    @GetMapping("/getById/{notificationId}")
    public ResponseEntity<?> getById(@PathVariable Integer notificationId) {
        return ResponseEntity.ok(notificationService.getById(notificationId));
    }

    @GetMapping("/getAllList")
    public ResponseEntity<List<NotificationResponse>> getAllList() {
        return ResponseEntity.ok(notificationService.listAll());
    }
}
