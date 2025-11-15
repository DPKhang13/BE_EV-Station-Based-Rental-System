package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "transactionhistory")
public class TransactionHistory {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    private BigDecimal amount;
    private String status;
    private String type;
    private LocalDateTime createdAt;
    @PrePersist
    public void onCreate()
    {
        this.createdAt = LocalDateTime.now();
    }



}
