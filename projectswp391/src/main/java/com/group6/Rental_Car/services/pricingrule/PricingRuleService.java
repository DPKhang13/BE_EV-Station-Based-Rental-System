package com.group6.Rental_Car.services.pricingrule;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.dtos.pricingrule.PricingRuleUpdateRequest;
import com.group6.Rental_Car.entities.Coupon;
import com.group6.Rental_Car.entities.PricingRule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PricingRuleService {
    PricingRule getPricingRuleByCarmodel(String carmodel);

    BigDecimal calculateRentalPrice(PricingRule pricingRule, LocalDate startDate, LocalDate endDate);

    BigDecimal applyLateFee(PricingRule pricingRule, long lateDays);

    BigDecimal applyCoupon(BigDecimal basePrice, Coupon coupon);

    List<PricingRuleResponse> getAllPricingRules();

    PricingRuleResponse updatePricingRule(String carmodel, PricingRuleUpdateRequest req);
}
