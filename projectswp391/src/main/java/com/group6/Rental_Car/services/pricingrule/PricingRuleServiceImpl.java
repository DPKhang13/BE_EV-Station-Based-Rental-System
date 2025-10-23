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
        //      TÍNH GIÁ CƠ BẢN (GIỜ/NGÀY)
        // =============================
        @Override
        public BigDecimal calculateRentalPrice(PricingRule pricingRule, long rentedHours) {
            if (pricingRule == null || rentedHours <= 0)
                throw new BadRequestException("Thiếu thông tin tính giá thuê");

            BigDecimal total;

            // Nếu thuê nguyên ngày (>= 24h)
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
        //      TÍNH TỔNG GIÁ (planned + actual + coupon)
        // =============================
        @Override
        public BigDecimal calculateTotalPrice(PricingRule rule, Coupon coupon,
                                              Integer plannedHours, long actualHours) {

            if (rule == null)
                throw new BadRequestException("Thiếu thông tin quy tắc giá");
            if (plannedHours == null || plannedHours <= 0)
                throw new BadRequestException("Thiếu thông tin thời lượng thuê");

            int baseHours = rule.getBaseHours();
            BigDecimal basePrice = safeValue(rule.getBaseHoursPrice());
            BigDecimal extraPrice = safeValue(rule.getExtraHourPrice());
            BigDecimal dailyPrice = safeValue(rule.getDailyPrice());
            BigDecimal total = BigDecimal.ZERO;

            // =============================
            // Tính tiền cho plannedHours
            // =============================
            if (plannedHours >= 24) {
                //  Thuê nguyên ngày → tính theo dailyPrice
                long days = plannedHours / 24;
                int remainder = plannedHours % 24;

                total = dailyPrice.multiply(BigDecimal.valueOf(days));

                // Nếu dư < 24h → tính tiếp phần dư theo base/extra
                if (remainder > 0) {
                    if (remainder < baseHours) {
                        // Nếu dư ít hơn block → tính theo giờ
                        total = total.add(extraPrice.multiply(BigDecimal.valueOf(remainder)));
                    } else {
                        //  Nếu dư đủ 1 hoặc nhiều block → tính block bình thường
                        int fullBlock = remainder / baseHours;
                        int rem = remainder % baseHours;

                        total = total.add(basePrice.multiply(BigDecimal.valueOf(fullBlock)));

                        if (rem > 0) {
                            total = total.add(extraPrice.multiply(BigDecimal.valueOf(rem)));
                        }
                    }
                }

            } else {
                //  Dưới 24h → tính theo block giờ
                if (plannedHours <= baseHours) {
                    total = basePrice;
                } else {
                    int fullBlock = plannedHours / baseHours;
                    int remainder = plannedHours % baseHours;

                    total = basePrice.multiply(BigDecimal.valueOf(fullBlock));

                    if (remainder > 0) {
                        total = total.add(extraPrice.multiply(BigDecimal.valueOf(remainder)));
                    }
                }
            }

            // =============================
            //  Nếu actualHours > plannedHours → cộng thêm phí vượt giờ
            // =============================
            if (actualHours > plannedHours) {
                long exceeded = actualHours - plannedHours;
                BigDecimal penalty = extraPrice.multiply(BigDecimal.valueOf(exceeded));
                total = total.add(penalty);
            }

            if (coupon != null && coupon.getDiscount() != null) {
                validateCoupon(coupon);
                BigDecimal discount = safeValue(coupon.getDiscount());

                if (discount.compareTo(BigDecimal.ZERO) > 0) {
                    if (discount.compareTo(BigDecimal.ONE) < 0) {
                        total = total.subtract(total.multiply(discount)); // %
                    } else {
                        total = total.subtract(discount); // cố định
                    }
                    if (total.compareTo(BigDecimal.ZERO) < 0)
                        total = BigDecimal.ZERO;
                }
            }

            return total;
        }

        // =============================
        //      XỬ LÝ THANH TOÁN THÀNH CÔNG
        // =============================
        @Transactional
        public void handlePaymentSuccess(Payment payment) {
            if (payment == null || payment.getRentalOrder() == null)
                throw new BadRequestException("Thanh toán không hợp lệ");

            RentalOrder order = payment.getRentalOrder();
            short type = payment.getType(); // SMALLINT trong DB

            switch (type) {
                case 1 -> { // Thanh toán thuê xe
                    order.setStatus("IN_USE");
                    rentalOrderRepository.save(order);
                }
                case 2 -> { // Thanh toán phí phạt
                    Notification n = new Notification();
                    n.setUser(order.getCustomer());
                    n.setMessage("Bạn đã thanh toán phí phạt " + payment.getAmount() + "đ cho đơn #" + order.getOrderId());
                    n.setCreatedAt(LocalDateTime.now());
                    notificationRepository.save(n);
                }
                case 3 -> { // Hoàn tiền
                    Notification n = new Notification();
                    n.setUser(order.getCustomer());
                    n.setMessage("Đã hoàn tiền " + payment.getAmount() + "đ cho đơn #" + order.getOrderId());
                    n.setCreatedAt(LocalDateTime.now());
                    notificationRepository.save(n);
                }
            }

            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
        }

        // =============================
        //      QUẢN LÝ RULE
        // =============================
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

        // =============================
        //      HÀM PHỤ TRỢ
        // =============================
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
