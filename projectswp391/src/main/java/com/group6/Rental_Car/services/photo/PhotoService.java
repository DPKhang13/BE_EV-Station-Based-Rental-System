package com.group6.Rental_Car.services.photo;

import com.group6.Rental_Car.entities.Photo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoService {

    Photo saveUserPhoto(UUID userId, String url, String type);

    List<Photo> getUserPhotos(UUID userId);

    Optional<Photo> getLatestUserPhoto(UUID userId, String type);

    void deletePhoto(Long photoId, UUID userId);
}
