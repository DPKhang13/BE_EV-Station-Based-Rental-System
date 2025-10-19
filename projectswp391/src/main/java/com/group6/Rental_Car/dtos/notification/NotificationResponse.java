package com.group6.Rental_Car.dtos.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Integer notificationId;
    private String message;
    private LocalDateTime createdAt;
    private UUID userId;
}
