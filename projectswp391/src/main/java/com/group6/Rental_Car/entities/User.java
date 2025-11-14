package com.group6.Rental_Car.entities;

import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "\"user\"") // 'User' là keyword trong SQL Server nên để trong []
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID userId;

    private String fullName;

    private String password;

    private String phone;
    @NotNull
    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<RentalOrder> rentalOrders;

    @ManyToOne
    @JoinColumn(name = "station_id")
    private RentalStation rentalStation;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<TransactionHistory> transactionhistory = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Notification> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "staff", fetch = FetchType.LAZY)
    private List<EmployeeSchedule> employeeSchedules;


}
