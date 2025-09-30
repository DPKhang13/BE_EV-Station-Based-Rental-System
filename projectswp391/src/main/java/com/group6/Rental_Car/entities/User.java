package com.group6.Rental_Car.entities;
import com.group6.Rental_Car.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "[User]") // vì 'User' có thể là keyword trong SQL Server nên để trong []
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // user_id INT AUTO INCREMENT
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email",nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING) // ánh xạ Enum Role -> NVARCHAR
    @Column(name = "role", length = 20, nullable = false)
    private Role role;

    @Column(name = "kyc_status", length = 50)
    private String kycStatus;
}
