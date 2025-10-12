package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Feedback;
import com.group6.Rental_Car.entities.RentalOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback,Integer> {
    Optional<Feedback> findByOrder(RentalOrder order);
    boolean existsByOrder(RentalOrder order); // 1 order chỉ cho 1 feedback
}
