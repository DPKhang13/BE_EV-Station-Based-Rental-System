package com.group6.Rental_Car.dtos.pricingrule;

import com.group6.Rental_Car.entities.PricingRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PricingRuleResponse {
    private Integer pricingRuleId;
    private Long vehicleId;
    private Integer baseHours;

    private BigDecimal baseHoursPrice;

    private BigDecimal extraHourPrice;

    private BigDecimal dailyPrice;
}
