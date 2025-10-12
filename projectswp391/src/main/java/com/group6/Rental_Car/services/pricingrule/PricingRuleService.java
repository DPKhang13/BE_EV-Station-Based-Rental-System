package com.group6.Rental_Car.services.pricingrule;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.dtos.pricingrule.PricingRuleUpdateRequest;
import com.group6.Rental_Car.entities.Coupon;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.entities.Vehicle;

import java.math.BigDecimal;
import java.util.List;

public interface PricingRuleService {
    PricingRule getPricingRuleByVehicle(Vehicle vehicle);

    BigDecimal calculateTotalPrice(PricingRule pricingRule, Coupon coupon);
    List<PricingRuleResponse> getAllPricingRules();
    PricingRuleResponse updatePricingRule(Long vehicleId,PricingRuleUpdateRequest req);
}
