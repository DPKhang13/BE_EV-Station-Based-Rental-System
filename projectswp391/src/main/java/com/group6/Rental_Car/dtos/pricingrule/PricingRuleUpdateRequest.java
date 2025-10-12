package com.group6.Rental_Car.dtos.pricingrule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
@Data
@AllArgsConstructor
@Builder
public class PricingRuleUpdateRequest {
    private Integer baseHours;

    private BigDecimal baseHoursPrice;

    private BigDecimal extraHourPrice;

    private BigDecimal dailyPrice;
}
