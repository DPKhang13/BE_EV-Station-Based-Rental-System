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
    private UUID userId;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private UserStatus status;
    private Integer stationId;
}
