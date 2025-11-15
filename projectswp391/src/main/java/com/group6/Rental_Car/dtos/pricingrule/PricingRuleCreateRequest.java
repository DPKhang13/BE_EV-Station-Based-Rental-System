package com.group6.Rental_Car.dtos.pricingrule;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Data

public class PricingRuleCreateRequest {

    @NotNull
    private Integer seatCount;

    @NotBlank
    private String variant;

    @Min(0)
    private BigDecimal dailyPrice;

    @Min(0)
    private BigDecimal lateFreePerDayPrice;

    @Min(0)
    private BigDecimal holidayPrice;
}
