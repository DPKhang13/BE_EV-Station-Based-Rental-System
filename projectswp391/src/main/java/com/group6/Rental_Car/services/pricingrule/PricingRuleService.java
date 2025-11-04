package com.group6.Rental_Car.services.pricingrule;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.dtos.pricingrule.PricingRuleUpdateRequest;
import com.group6.Rental_Car.entities.Coupon;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.entities.Vehicle;

import java.math.BigDecimal;
import java.util.List;

public interface PricingRuleService {
    PricingRule getPricingRuleBySeatAndVariant(Integer seatCount, String variant);

    BigDecimal calculateRentalPrice(PricingRule pricingRule, long rentedHours);
    BigDecimal calculateTotalPrice(PricingRule pricingRule, Coupon coupon, Integer plannedHours, long actualHours);


    List<PricingRuleResponse> getAllPricingRules();

    PricingRuleResponse updatePricingRule(Integer seatCount, String variant, PricingRuleUpdateRequest req);
}
