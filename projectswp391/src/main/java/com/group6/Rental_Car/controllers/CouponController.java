package com.group6.Rental_Car.controllers;


import com.group6.Rental_Car.services.coupon.CouponService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coupon")
@Tag(name = "API Coupon",description = "Lay danh sach ma giam gia")
public class CouponController {
    @Autowired
    private CouponService couponService;
    @GetMapping("/showall")
    public ResponseEntity<List<?>> getAllCoupons(){
        return ResponseEntity.ok(couponService.getAllCoupons());
    }

}

