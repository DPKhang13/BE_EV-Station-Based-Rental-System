package com.group6.Rental_Car.dtos.loginpage;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterAccountDto {
    @NotBlank(message = "Full name is required")
    private String fullName;
    @Email(message = "Wrong email")
    private String email;

    @Size(min = 6, max = 200)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one lowercase, one uppercase, one digit, and one special character"
    )
    private String password;
    @NotBlank(message = "Phone number is required")
    private String phone;
}
