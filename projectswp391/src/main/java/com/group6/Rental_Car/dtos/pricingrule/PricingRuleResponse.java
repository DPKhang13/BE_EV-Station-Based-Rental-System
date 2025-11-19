package com.group6.Rental_Car.dtos.pricingrule;

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
    private String carmodel;

    private BigDecimal dailyPrice;
    private BigDecimal lateFeePerDay;
    private BigDecimal holidayPrice;
}
