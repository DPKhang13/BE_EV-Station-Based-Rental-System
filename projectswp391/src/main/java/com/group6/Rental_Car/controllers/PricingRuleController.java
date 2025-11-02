package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.dtos.pricingrule.PricingRuleUpdateRequest;
import com.group6.Rental_Car.services.pricingrule.PricingRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricingrules")
@RequiredArgsConstructor
@Tag(name = "API PricingRule", description = "Lấy bảng giá và cập nhật bảng giá theo seatCount & variant")
public class PricingRuleController {

    private final PricingRuleService pricingRuleService;

    @GetMapping("/get")
    public ResponseEntity<List<?>> getAllPricingRules() {
        List<PricingRuleResponse> response = pricingRuleService.getAllPricingRules();
        return ResponseEntity.ok(response);
    }
    @PutMapping("/update/{seatCount}/{variant}")
    public ResponseEntity<?> updatePricingRule(
            @PathVariable Integer seatCount,
            @PathVariable String variant,
            @RequestBody PricingRuleUpdateRequest req
    ) {
        PricingRuleResponse response = pricingRuleService.updatePricingRule(seatCount, variant, req);
        return ResponseEntity.ok(response);
    }
}
