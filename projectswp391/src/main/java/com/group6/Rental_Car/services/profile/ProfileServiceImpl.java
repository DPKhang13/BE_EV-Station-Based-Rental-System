package com.group6.Rental_Car.services.profile;

import com.group6.Rental_Car.dtos.profile.ProfileDto;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.UserRepository;

import com.group6.Rental_Car.services.storage.StorageService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class ProfileServiceImpl implements ProfileService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private StorageService storageService;

    @Override
    public ProfileDto updateProfile(ProfileDto profile, UUID userId) {
        User account = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        modelMapper.map(profile, account);

        account = userRepository.save(account);
        return modelMapper.map(account, ProfileDto.class);
    }

}
