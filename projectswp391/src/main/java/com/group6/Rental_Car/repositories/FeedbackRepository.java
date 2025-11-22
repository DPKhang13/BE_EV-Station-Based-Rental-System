package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Feedback;
import com.group6.Rental_Car.entities.RentalOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback,Integer> {
    Optional<Feedback> findByOrder(RentalOrder order);
    List<Feedback> findAllByOrder(RentalOrder order);
    boolean existsByOrder(RentalOrder order); // 1 order chỉ cho 1 feedback
    //Admin Dashboard
    @Query("select coalesce(avg(f.rating),0) from Feedback f")
    Double avgRating();

    @Query("select f.rating as rating, count(f) as total from Feedback f group by f.rating order by f.rating")
    List<Object[]> ratingDistribution();
}
