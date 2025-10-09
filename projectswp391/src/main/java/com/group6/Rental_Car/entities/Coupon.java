package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;


import java.text.DecimalFormat;
import java.time.LocalDate;

@Entity
@Table(name = "coupon")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;
    private DecimalFormat discount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String status;


}
