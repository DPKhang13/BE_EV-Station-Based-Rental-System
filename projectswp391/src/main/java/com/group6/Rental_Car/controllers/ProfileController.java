package com.group6.Rental_Car.controllers;



import com.group6.Rental_Car.dtos.profile.ProfileDto;
import com.group6.Rental_Car.services.profile.ProfileService;

import com.group6.Rental_Car.utils.JwtUserDetails;
import com.group6.Rental_Car.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile Api", description ="read,update profile")
public class ProfileController {
    @Autowired
    private ProfileService profileService;
    @Autowired
    private JwtUtil JwtUtil;

    @PostMapping("/update")
    @Operation(summary = "Update account profile",
                description = "Update the profile and return the updated profile")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileDto profileDto,
                                            @AuthenticationPrincipal JwtUserDetails userDetails){
    return ResponseEntity.ok().body(profileService.updateProfile(profileDto,userDetails.getUserId()));
    }

}
