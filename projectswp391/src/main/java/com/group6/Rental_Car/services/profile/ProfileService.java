package com.group6.Rental_Car.services.profile;

import com.group6.Rental_Car.dtos.profile.ProfileDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ProfileService {
    ProfileDto updateProfile(ProfileDto profile, UUID accountId);
}
