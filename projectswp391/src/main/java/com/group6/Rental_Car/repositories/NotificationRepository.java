package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Notification;
import com.group6.Rental_Car.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
List<Notification> findByUserId(User userId);
}
