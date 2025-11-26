package com.group6.Rental_Car.services.photo;

import com.group6.Rental_Car.entities.Photo;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.repositories.PhotoRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoServiceImpl implements PhotoService {

    private final PhotoRepository photoRepo;
    private final UserRepository userRepo;

    @Override
    @Transactional
    public Photo saveUserPhoto(UUID userId, String url, String type) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Optional<Photo> existing = photoRepo.findFirstByUser_UserIdAndTypeOrderByUploadedAtDesc(userId, type);

        Photo entity;

        if (existing.isPresent()) {
            entity = existing.get();
            entity.setPhotoUrl(url);
            entity.setUploadedAt(LocalDateTime.now());
            System.out.println("🔄 Updated existing " + type + " photo for user " + userId);
        } else {
            entity = Photo.builder()
                    .user(user)
                    .photoUrl(url)
                    .type(type)
                    .uploadedAt(LocalDateTime.now())
                    .build();
            System.out.println("🆕 Created new " + type + " photo for user " + userId);
        }

        return photoRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Photo> getUserPhotos(UUID userId) {
        return photoRepo.findByUser_UserIdOrderByUploadedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Photo> getLatestUserPhoto(UUID userId, String type) {
        return photoRepo.findFirstByUser_UserIdAndTypeOrderByUploadedAtDesc(userId, type);
    }

    @Override
    @Transactional
    public void deletePhoto(Long photoId, UUID userId) {
        Photo p = photoRepo.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        if (!p.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner");
        }
        photoRepo.delete(p);
    }

}
