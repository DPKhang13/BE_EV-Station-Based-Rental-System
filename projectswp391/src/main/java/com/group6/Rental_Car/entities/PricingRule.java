package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "pricingrule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pricingrule_id")
    private Integer pricingRuleId;

    @Column(name = "carmodel", length = 50)
    private String carmodel;


    @Column(nullable = false)
    private BigDecimal dailyPrice;

    @Column(name = "holiday_price", precision = 18, scale = 2)
    private BigDecimal holidayPrice;

    @Column(name = "late_fee_per_day", precision = 18, scale = 2)
    private BigDecimal lateFeePerDay = BigDecimal.ZERO;

    @OneToMany(mappedBy = "pricingRule", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VehicleModel> vehicleModels;
}
