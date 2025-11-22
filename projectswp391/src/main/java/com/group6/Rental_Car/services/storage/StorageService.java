package com.group6.Rental_Car.services.storage;

import com.group6.Rental_Car.entities.Photo;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

public interface StorageService {
    String uploadPublic(String folder, MultipartFile file ) throws IOException;

}
