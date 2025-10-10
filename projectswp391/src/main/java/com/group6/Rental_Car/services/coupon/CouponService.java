package com.group6.Rental_Car.services.coupon;

import com.group6.Rental_Car.entities.Coupon;

import java.util.List;

public interface CouponService {

    Coupon getValidCouponByCode(String code);


    List<Coupon> getAllCoupons();

    Coupon getCouponById(Integer couponId);
}
