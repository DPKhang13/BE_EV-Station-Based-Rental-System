package com.group6.Rental_Car.dtos.loginpage;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.enums.UserStatus;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDtoResponse {
    private UUID userId;       // id vừa tạo
    private String fullName;   // tên người dùng
    private String email;      // email
    private String phone;
    private Role role;         // quyền (Enum)
    private UserStatus status; // trạng thái
}
