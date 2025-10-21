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

    private Integer seatCount;

    private String variant;

    private Integer baseHours;

    private BigDecimal baseHoursPrice;

    private BigDecimal extraHourPrice;

    private BigDecimal dailyPrice;
    @OneToMany(mappedBy = "pricingRule", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VehicleModel> vehicleModels;
}
