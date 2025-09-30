package com.group6.Rental_Car.services.authencation;

import com.group6.Rental_Car.dtos.authencation.LoginRequest;
import com.group6.Rental_Car.dtos.authencation.LoginResponse;
import com.group6.Rental_Car.entities.User;

import java.util.Optional;

public interface UserService {
    Optional<LoginResponse> login(LoginRequest request);
    User register(User user);
    Optional<User> findByEmail(String email);
}
