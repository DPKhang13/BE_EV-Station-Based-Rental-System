package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.entities.Photo;
import com.group6.Rental_Car.repositories.PhotoRepository;
import com.group6.Rental_Car.services.photo.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final PhotoRepository photoRepo;

    // ✅ Lấy tất cả ảnh của user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Photo>> getPhotosByUser(@PathVariable UUID userId) {
        List<Photo> photos = photoService.getUserPhotos(userId);
        return ResponseEntity.ok(photos);
    }

    // ✅ Endpoint phụ (phòng trường hợp FE gọi nhầm)
    @GetMapping("/{userId}")
    public ResponseEntity<List<Photo>> getPhotosSimple(@PathVariable UUID userId) {
        List<Photo> photos = photoService.getUserPhotos(userId);
        return ResponseEntity.ok(photos);
    }

    // ✅ Trả về trạng thái giấy tờ (để FE check)
    @GetMapping("/doc-status/{userId}")
    public ResponseEntity<Map<String, Boolean>> getDocStatus(@PathVariable UUID userId) {
        boolean hasCCCD = photoRepo.existsByUser_UserIdAndTypeIgnoreCase(userId, "CCCD");
        boolean hasGPLX = photoRepo.existsByUser_UserIdAndTypeIgnoreCase(userId, "GPLX");
        return ResponseEntity.ok(Map.of(
                "hasIdCard", hasCCCD,
                "hasDriverLicense", hasGPLX
        ));
    }
}
