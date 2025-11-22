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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PricingRuleServiceImpl implements PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final ModelMapper modelMapper;

    @Override
    public PricingRule getPricingRuleByCarmodel(String carmodel) {
        return pricingRuleRepository.findByCarmodelIgnoreCase(carmodel)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy quy tắc giá cho carmodel = " + carmodel
                ));
    }

    @Override
    public BigDecimal calculateRentalPrice(PricingRule pricingRule, LocalDate startDate, LocalDate endDate) {
        if (pricingRule == null) throw new BadRequestException("Thiếu quy tắc giá");
        if (startDate == null || endDate == null || !endDate.isAfter(startDate))
            throw new BadRequestException("Ngày thuê không hợp lệ");

        // Tính số ngày: từ startDate đến endDate (KHÔNG bao gồm endDate vì đó là ngày trả)
        // Ví dụ: 23/11 đến 28/11 = 5 ngày (23, 24, 25, 26, 27) - ngày 28 là ngày trả, không tính
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        BigDecimal total = BigDecimal.ZERO;

        System.out.println("📅 [calculateRentalPrice] Start: " + startDate + ", End: " + endDate + ", Days: " + days);

        for (int i = 0; i < days; i++) {
            LocalDate current = startDate.plusDays(i);
            BigDecimal dayPrice = pricingRule.getDailyPrice();

            if (isWeekend(current) && pricingRule.getHolidayPrice() != null) {
                dayPrice = pricingRule.getHolidayPrice();
                System.out.println("📅 [calculateRentalPrice] " + current + " (WEEKEND) = " + dayPrice);
            } else {
                System.out.println("📅 [calculateRentalPrice] " + current + " (WEEKDAY) = " + dayPrice);
            }

            total = total.add(dayPrice);
        }

        System.out.println("💰 [calculateRentalPrice] Total: " + total);
        return total;
    }

    /**
     * Tính phí trễ hạn
     */
    @Override
    public BigDecimal applyLateFee(PricingRule pricingRule, long lateDays) {
        if (pricingRule == null) throw new BadRequestException("Thiếu quy tắc giá");
        if (lateDays <= 0) return BigDecimal.ZERO;

        BigDecimal lateFee = pricingRule.getLateFeePerDay() != null
                ? pricingRule.getLateFeePerDay()
                : BigDecimal.ZERO;

        return lateFee.multiply(BigDecimal.valueOf(lateDays));
    }

    /**
     * Áp dụng mã giảm giá
     */
    @Override
    public BigDecimal applyCoupon(BigDecimal basePrice, Coupon coupon) {
        if (coupon == null || basePrice == null) return basePrice;

        validateCoupon(coupon);
        BigDecimal discount = coupon.getDiscount();
        if (discount == null) return basePrice;

        BigDecimal result = basePrice;
        if (discount.compareTo(BigDecimal.ONE) < 0) {
            // giảm theo %
            result = basePrice.subtract(basePrice.multiply(discount));
        } else {
            // giảm theo số tiền
            result = basePrice.subtract(discount);
        }

        return result.max(BigDecimal.ZERO);
    }

    @Override
    public List<PricingRuleResponse> getAllPricingRules() {
        return pricingRuleRepository.findAll()
                .stream()
                .map(rule -> modelMapper.map(rule, PricingRuleResponse.class))
                .collect(Collectors.toList());
    }

    @Override
    public PricingRuleResponse updatePricingRule(String carmodel, PricingRuleUpdateRequest req) {
        PricingRule rule = pricingRuleRepository.findByCarmodelIgnoreCase(carmodel)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy quy tắc giá cho carmodel = " + carmodel
                ));

        rule.setDailyPrice(req.getDailyPrice());
        rule.setLateFeePerDay(req.getLateFeePerDay());
        rule.setHolidayPrice(req.getHolidayPrice());

        PricingRule updated = pricingRuleRepository.save(rule);
        return modelMapper.map(updated, PricingRuleResponse.class);
    }

    private void validateCoupon(Coupon coupon) {
        LocalDate today = LocalDate.now();
        if (coupon.getValidFrom() != null && today.isBefore(coupon.getValidFrom()))
            throw new BadRequestException("Coupon chưa có hiệu lực");
        if (coupon.getValidTo() != null && today.isAfter(coupon.getValidTo()))
            throw new BadRequestException("Coupon đã hết hạn");
        if (!"active".equalsIgnoreCase(coupon.getStatus()))
            throw new BadRequestException("Coupon không khả dụng");
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }
}
