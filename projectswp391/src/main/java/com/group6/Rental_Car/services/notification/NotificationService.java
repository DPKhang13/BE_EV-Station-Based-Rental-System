package com.group6.Rental_Car.services.notification;

import com.group6.Rental_Car.dtos.notification.NotificationCreateRequest;
import com.group6.Rental_Car.dtos.notification.NotificationResponse;
import com.group6.Rental_Car.dtos.notification.NotificationUpdateRequest;

import java.util.List;

public interface NotificationService {
    NotificationResponse create(NotificationCreateRequest req);
    NotificationResponse update(Integer notificationId, NotificationUpdateRequest req);
    void delete(Integer notificationId);
    NotificationResponse getById(Integer notificationId);
    List<NotificationResponse> listAll();
}
