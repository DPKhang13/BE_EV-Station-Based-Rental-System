package com.group6.Rental_Car.services.coupon;

import com.group6.Rental_Car.entities.Coupon;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService{
    private final CouponRepository couponRepository;

    @Override
    public Coupon getValidCouponByCode(String code) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with code: " + code));

        validateCoupon(coupon);
        return coupon;
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
    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    @Override
    public Coupon getCouponByCode(String couponCode) {
        return couponRepository.findByCodeIgnoreCase(couponCode)
         .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with code: " + couponCode));
    }
    @Override
    public BigDecimal applyCouponIfValid(Coupon coupon, BigDecimal basePrice) {
        if (coupon == null) return basePrice; // không có coupon → giữ nguyên

        validateCoupon(coupon);

        BigDecimal discount = coupon.getDiscount() != null ? coupon.getDiscount() : BigDecimal.ZERO;
        BigDecimal total = basePrice;

        // Nếu discount < 1 → giảm theo %
        if (discount.compareTo(BigDecimal.ONE) < 0) {
            total = basePrice.subtract(basePrice.multiply(discount));
        } else {
            // Nếu discount >= 1 → giảm theo giá cố định
            total = basePrice.subtract(discount);
        }

        // Không bao giờ âm giá
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        return total;
    }
}