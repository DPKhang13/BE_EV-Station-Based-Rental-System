package com.group6.Rental_Car.services.pricingrule;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.dtos.pricingrule.PricingRuleUpdateRequest;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricingRuleServiceImpl implements PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final RentalOrderRepository rentalOrderRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    private final ModelMapper modelMapper;

    // =============================
    //      LẤY RULE THEO XE
    // =============================
    @Override
    public PricingRule getPricingRuleBySeatAndVariant(Integer seatCount, String variant) {
        return pricingRuleRepository.findBySeatCountAndVariantIgnoreCase(seatCount, variant)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy quy tắc giá cho seatCount = " + seatCount + " và variant = " + variant
                ));
    }

    // =============================
    //      TÍNH GIÁ CƠ BẢN THEO GIỜ / NGÀY
    // =============================
    @Override
    public BigDecimal calculateRentalPrice(PricingRule pricingRule, long rentedHours) {
        if (pricingRule == null || rentedHours <= 0)
            throw new BadRequestException("Thiếu thông tin tính giá thuê");

        BigDecimal total;

        // Nếu thuê nguyên ngày (>= 24h) → tính theo daily_price
        if (rentedHours >= 24) {
            long days = (long) Math.ceil(rentedHours / 24.0);
            total = pricingRule.getDailyPrice().multiply(BigDecimal.valueOf(days));
        } else {
            int baseHours = pricingRule.getBaseHours();
            BigDecimal basePrice = safeValue(pricingRule.getBaseHoursPrice());
            BigDecimal extraPrice = safeValue(pricingRule.getExtraHourPrice());

            if (rentedHours <= baseHours) {
                total = basePrice;
            } else {
                long extraHours = rentedHours - baseHours;
                total = basePrice.add(extraPrice.multiply(BigDecimal.valueOf(extraHours)));
            }
        }

        return total;
    }

    // =============================
    //      TÍNH GIÁ TỔNG (CÓ PHẠT + COUPON)
    // =============================
    @Override
    public BigDecimal calculateTotalPrice(PricingRule pricingRule,
                                          Coupon coupon,
                                          Integer plannedHours,
                                          long actualHours) {
        if (pricingRule == null)
            throw new BadRequestException("Thiếu thông tin quy tắc giá");

        if (plannedHours == null || plannedHours <= 0)
            throw new BadRequestException("Thiếu thông tin thời lượng thuê");

        long hours = (actualHours > 0) ? actualHours : plannedHours;

        BigDecimal total;

        // Nếu thuê >= 24 giờ thì tính theo giá ngày
        if (hours >= 24) {
            long days = hours / 24;
            total = pricingRule.getDailyPrice().multiply(BigDecimal.valueOf(days));
        } else {
            int baseHours = pricingRule.getBaseHours();
            BigDecimal basePrice = safeValue(pricingRule.getBaseHoursPrice());
            BigDecimal extraPrice = safeValue(pricingRule.getExtraHourPrice());

            if (hours <= baseHours) {
                total = basePrice;
            } else {
                long extraHours = hours - baseHours;
                total = basePrice.add(extraPrice.multiply(BigDecimal.valueOf(extraHours)));
            }
        }

        // Áp dụng coupon nếu có
        if (coupon != null) {
            validateCoupon(coupon);
            BigDecimal discount = safeValue(coupon.getDiscount());
            if (discount.compareTo(BigDecimal.ZERO) > 0) {
                if (discount.compareTo(BigDecimal.ONE) < 0) {
                    total = total.subtract(total.multiply(discount)); // giảm %
                } else {
                    total = total.subtract(discount); // giảm số tiền cụ thể
                }
                if (total.compareTo(BigDecimal.ZERO) < 0)
                    total = BigDecimal.ZERO;
            }
        }

        return total;
    }


    @Transactional
    public void handlePaymentSuccess(Payment payment) {
        if (payment == null || payment.getRentalOrder() == null)
            throw new BadRequestException("Thanh toán không hợp lệ");

        RentalOrder order = payment.getRentalOrder();

        short type = payment.getType(); // SMALLINT trong DB

        if (type == 1) {
            // 1 = thanh toán đơn thuê xe
            order.setStatus("IN_USE");
            rentalOrderRepository.save(order);

        } else if (type == 2) {
            // 2 = thanh toán phí phạt
            Notification notification = new Notification();
            notification.setUser(order.getCustomer());
            notification.setMessage("Bạn đã thanh toán phí phạt "
                    + payment.getAmount() + "đ cho đơn thuê #" + order.getOrderId());
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notification);

        } else if (type == 3) {
            // 3 = hoàn tiền
            Notification notification = new Notification();
            notification.setUser(order.getCustomer());
            notification.setMessage("Đã hoàn tiền "
                    + payment.getAmount() + "đ cho đơn thuê #" + order.getOrderId());
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }

        // Cập nhật trạng thái thanh toán
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
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

    private BigDecimal safeValue(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
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
}
