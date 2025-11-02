package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.services.storage.StorageServiceImpl;
import com.group6.Rental_Car.services.photo.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final StorageServiceImpl storage;
    private final PhotoService photoService; // <-- thêm service lưu DB

    @PostMapping(value = "/cccd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadCCCD(
            @RequestPart("file") MultipartFile file,
            @RequestParam("userId") UUID userId
    ) throws Exception {

        // up vào folder theo userId
        String folder = "users/" + userId + "/cccd";
        String url = storage.uploadPublic(folder, file);

        // lưu DB bảng photo
        var saved = photoService.saveUserPhoto(userId, url, "CCCD");

        return ResponseEntity.ok(Map.of(
                "photoId", saved.getPhotoId(),
                "url",     saved.getPhotoUrl(),
                "type",    saved.getType()
        ));

    }

    // Bằng lái -> public + lưu DB (type = "driver-license")
    @PostMapping(value = "/driver-license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDriverLicense(
            @RequestPart("file") MultipartFile file,
            @RequestParam("userId") UUID userId
    ) throws Exception {

        String folder = "users/" + userId + "/driver-license";
        String url = storage.uploadPublic(folder, file);

        var saved = photoService.saveUserPhoto(userId, url, "GPLX");

        return ResponseEntity.ok(Map.of(
                "photoId", saved.getPhotoId(),
                "url",     saved.getPhotoUrl(),
                "type",    saved.getType()
        ));
    }
}
