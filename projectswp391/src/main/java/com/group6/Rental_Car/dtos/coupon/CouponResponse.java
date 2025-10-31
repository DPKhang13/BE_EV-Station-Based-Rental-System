package com.group6.Rental_Car.dtos.coupon;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CouponResponse {
    private Integer couponId;
    private String code;
    private BigDecimal discount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String status;
}
