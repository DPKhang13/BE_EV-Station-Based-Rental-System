package com.group6.Rental_Car.services.pricingrule;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.dtos.pricingrule.PricingRuleUpdateRequest;
import com.group6.Rental_Car.entities.Coupon;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricingRuleServiceImpl implements PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final ModelMapper modelMapper;

    @Override
    public PricingRule getPricingRuleBySeatAndVariant(Integer seatCount, String variant) {
        return pricingRuleRepository.findBySeatCountAndVariantIgnoreCase(seatCount, variant)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy quy tắc giá cho seatCount = " + seatCount + " và variant = " + variant
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
                // < 1 => %; >= 1 => số tiền
                if (discount.compareTo(BigDecimal.ONE) < 0) {
                    total = total.subtract(total.multiply(discount));
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
        for (BigDecimal v : values) {
            if (v != null) sum = sum.add(v);
        }
        return sum;
    }

    private BigDecimal safeValue(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void validateCoupon(Coupon coupon) {
        LocalDate today = LocalDate.now();
        if (coupon.getValidFrom() != null && today.isBefore(coupon.getValidFrom())) {
            throw new BadRequestException("Coupon chưa có hiệu lực");
        }
        if (coupon.getValidTo() != null && today.isAfter(coupon.getValidTo())) {
            throw new BadRequestException("Coupon đã hết hạn");
        }
        if (!"active".equalsIgnoreCase(coupon.getStatus())) {
            throw new BadRequestException("Coupon không khả dụng");
        }
    }

    @Override
    public List<PricingRuleResponse> getAllPricingRules() {
        return pricingRuleRepository.findAll()
                .stream()
                .map(rule -> modelMapper.map(rule, PricingRuleResponse.class))
                .collect(Collectors.toList());
    }

    @Override
    public PricingRuleResponse updatePricingRule(Integer seatCount, String variant, PricingRuleUpdateRequest req) {
        PricingRule rule = pricingRuleRepository.findBySeatCountAndVariantIgnoreCase(seatCount, variant)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy quy tắc giá cho seatCount = " + seatCount + " và variant = " + variant
                ));

        rule.setBaseHours(req.getBaseHours());
        rule.setBaseHoursPrice(req.getBaseHoursPrice());
        rule.setExtraHourPrice(req.getExtraHourPrice());
        rule.setDailyPrice(req.getDailyPrice());

        PricingRule updated = pricingRuleRepository.save(rule);
        return modelMapper.map(updated, PricingRuleResponse.class);
    }
}
