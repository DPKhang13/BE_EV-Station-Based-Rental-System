package com.group6.Rental_Car.dtos.loginpage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.enums.UserStatus;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterResponse {
    @JsonIgnore
    private UUID userId;       // id vừa tạo
    private String fullName;   // tên người dùng
    private String email;      // email
    private String phone;
    @JsonIgnore
    private Role role;         // quyền (Enum)
    private UserStatus status; // trạng thái
}
