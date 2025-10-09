package com.group6.Rental_Car.services.pricingrule;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingRuleServiceImpl implements PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;

    @Override
    public PricingRule getPricingRuleByVehicle(Vehicle vehicle) {
        return pricingRuleRepository.findByVehicle(vehicle)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found for vehicle ID: " + vehicle.getVehicleId()));
    }

    @Override
    public BigDecimal calculateTotalPrice(PricingRule pricingRule) {
        BigDecimal total = BigDecimal.ZERO;

        if (pricingRule.getBaseHoursPrice() != null)
            total = total.add(pricingRule.getBaseHoursPrice());

        if (pricingRule.getExtraHourPrice() != null)
            total = total.add(pricingRule.getExtraHourPrice());

        if (pricingRule.getDailyPrice() != null)
            total = total.add(pricingRule.getDailyPrice());

        return total;
    }

    @Override
    public List<PricingRule> getAllPricingRules() {
        return pricingRuleRepository.findAll();
    }
}