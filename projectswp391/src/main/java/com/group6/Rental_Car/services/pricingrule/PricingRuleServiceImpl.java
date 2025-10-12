package com.group6.Rental_Car.services.pricingrule;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.dtos.pricingrule.PricingRuleUpdateRequest;
import com.group6.Rental_Car.entities.Coupon;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.entities.Vehicle;
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
    public List<PricingRuleResponse> getAllPricingRules() {
        return pricingRuleRepository.findAll()
                .stream()
                .map(rule -> {
                    PricingRuleResponse response = modelMapper.map(rule, PricingRuleResponse.class);
                    if (rule.getVehicle() != null) {
                        response.setVehicleId(rule.getVehicle().getVehicleId());
                    }
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    public PricingRuleResponse updatePricingRule(Long vehicleId, PricingRuleUpdateRequest req) {
        // 1️⃣ Tìm pricing rule theo vehicleId
        PricingRule pricingRule = pricingRuleRepository.findByVehicle_VehicleId(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc giá cho vehicleId = " + vehicleId));

        pricingRule.setBaseHours(req.getBaseHours());
        pricingRule.setBaseHoursPrice(req.getBaseHoursPrice());
        pricingRule.setExtraHourPrice(req.getExtraHourPrice());
        pricingRule.setDailyPrice(req.getDailyPrice());

        PricingRule updated = pricingRuleRepository.save(pricingRule);
        PricingRuleResponse response = modelMapper.map(updated, PricingRuleResponse.class);
        if (updated.getVehicle() != null) {
            response.setVehicleId(updated.getVehicle().getVehicleId());
        }
        return response;
    }


}
