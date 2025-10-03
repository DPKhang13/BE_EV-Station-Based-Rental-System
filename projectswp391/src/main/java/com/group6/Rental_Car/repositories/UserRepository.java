package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndPasswordAndRole(String email, String password, Role role);

}
