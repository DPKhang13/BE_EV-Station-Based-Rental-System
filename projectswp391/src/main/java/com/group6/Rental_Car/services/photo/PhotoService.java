package com.group6.Rental_Car.services;

import com.group6.Rental_Car.entities.Photo;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.repositories.PhotoRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoService {
    private final PhotoRepository photoRepo;
    private final UserRepository userRepo;

    @Transactional
    public Photo saveUserPhoto(UUID userId, String url, String type) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Photo p = Photo.builder()
                .user(user)
                .photoUrl(url)
                .type(type) // "cccd" | "driver-license"
                .uploadedAt(LocalDateTime.now())
                .build();

        return photoRepo.save(p);
    }
}
