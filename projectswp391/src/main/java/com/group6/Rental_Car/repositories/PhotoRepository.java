package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.Photo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByUser_UserIdOrderByUploadedAtDesc(UUID userId);
    Optional<Photo> findFirstByUser_UserIdAndTypeOrderByUploadedAtDesc(UUID userId, String type);
    boolean existsByUser_UserIdAndTypeIgnoreCase(UUID userId, String type);

}
