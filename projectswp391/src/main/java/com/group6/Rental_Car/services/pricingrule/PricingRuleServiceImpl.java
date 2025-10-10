package com.group6.Rental_Car.services.pricingrule;

import com.group6.Rental_Car.entities.Coupon;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingRuleServiceImpl implements PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;

    @Override
    public PricingRule getPricingRuleByVehicle(Vehicle vehicle) {
        return pricingRuleRepository.findByVehicle(vehicle)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pricing rule not found for vehicle ID: " + vehicle.getVehicleId()
                ));
    }

    @Override
    public BigDecimal calculateTotalPrice(PricingRule pricingRule, Coupon coupon) {
        BigDecimal total = safeAdd(
                pricingRule.getBaseHoursPrice(),
                pricingRule.getExtraHourPrice(),
                pricingRule.getDailyPrice()
        );

        if (coupon != null) {
            validateCoupon(coupon);

            BigDecimal discount = safeValue(coupon.getDiscount());
            if (discount.compareTo(BigDecimal.ZERO) > 0) {
                // Nếu < 1 => phần trăm; nếu >= 1 => số tiền
                if (discount.compareTo(BigDecimal.ONE) < 0) {
                    total = total.subtract(total.multiply(discount)); // ví dụ 0.1 = 10%
                } else {
                    total = total.subtract(discount);
                }

                if (total.compareTo(BigDecimal.ZERO) < 0)
                    total = BigDecimal.ZERO;
            }
        }

        return total;
    }

    private BigDecimal safeAdd(BigDecimal... values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null)
                sum = sum.add(value);
        }
        return sum;
    }

    // Helper thay null = 0
    private BigDecimal safeValue(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void validateCoupon(Coupon coupon) {
        LocalDate today = LocalDate.now();
        if (coupon.getValidFrom() != null && today.isBefore(coupon.getValidFrom())) {
            throw new BadRequestException("Coupon is not yet valid");
        }
        if (coupon.getValidTo() != null && today.isAfter(coupon.getValidTo())) {
            throw new BadRequestException("Coupon has expired");
        }
        if (!"active".equalsIgnoreCase(coupon.getStatus())) {
            throw new BadRequestException("Coupon is not active");
        }
    }

    @Override
    public List<PricingRule> getAllPricingRules() {
        return pricingRuleRepository.findAll();
    }
}
