package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;


import java.math.BigDecimal;
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
    @Column(name = "coupon_id")
    private Integer couponId;

    private String code;
    private BigDecimal discount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String status;


}
