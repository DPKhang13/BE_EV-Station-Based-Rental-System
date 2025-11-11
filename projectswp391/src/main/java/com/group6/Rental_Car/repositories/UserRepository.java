package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    User findFirstByEmail(String email);
    List<User> findByRole(Role role);

    boolean existsByEmailAndPassword(@Email String email, @Min(6) @Max(200) @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]") String password);
    //Admin Dashboard
    long countByRole(Role role);
    // 'admin' | 'staff' | 'customer'
    List<User> findByStatusIn(List<UserStatus> statuses);
    Optional<User> findByPhone(String phone);


}
