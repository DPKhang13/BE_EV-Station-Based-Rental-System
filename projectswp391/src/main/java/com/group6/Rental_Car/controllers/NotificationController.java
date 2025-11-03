package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.notification.NotificationCreateRequest;
import com.group6.Rental_Car.dtos.notification.NotificationResponse;
import com.group6.Rental_Car.dtos.notification.NotificationUpdateRequest;
import com.group6.Rental_Car.services.notification.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Notification API", description = "User nhận thông báo từ dịch vụ")
@RequestMapping("/api/notification")
public class NotificationController {
    private final NotificationService notificationService;
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody NotificationCreateRequest req) {
        return ResponseEntity.ok(notificationService.create(req));
    }

    @PutMapping("/update/{notificationId}")
    public ResponseEntity<NotificationResponse> update(@PathVariable Integer notificationId, @RequestBody NotificationUpdateRequest req) {
        return ResponseEntity.ok(notificationService.update(notificationId, req));
    }

    @DeleteMapping("/delete/{notificationId}")
    public ResponseEntity<?> delete(@PathVariable Integer notificationId) {
        notificationService.delete(notificationId);
        return ResponseEntity.ok("Deleted notification successfully");
    }

    @GetMapping("/getById/{notificationId}")
    public ResponseEntity<NotificationResponse> getById(@PathVariable Integer notificationId) {
        return ResponseEntity.ok(notificationService.getById(notificationId));
    }

    @GetMapping("/getAllList")
    public ResponseEntity<List<NotificationResponse>> getAllList() {
        return ResponseEntity.ok(notificationService.listAll());
    }
}
