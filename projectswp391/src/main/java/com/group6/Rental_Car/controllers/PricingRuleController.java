package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.dtos.pricingrule.PricingRuleUpdateRequest;
import com.group6.Rental_Car.services.pricingrule.PricingRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricing-rules")
@RequiredArgsConstructor
@Tag(name = "Pricing Rule API", description = "API quản lý bảng giá thuê xe theo carmodel")
public class PricingRuleController {

    private final PricingRuleService pricingRuleService;

    @GetMapping
    public ResponseEntity<List<PricingRuleResponse>> getAllPricingRules() {
        List<PricingRuleResponse> rules = pricingRuleService.getAllPricingRules();
        return ResponseEntity.ok(rules);
    }

    @PutMapping("/{carmodel}")
    public ResponseEntity<PricingRuleResponse> updatePricingRule(
            @PathVariable String carmodel,
            @Valid @RequestBody PricingRuleUpdateRequest request
    ) {
        PricingRuleResponse updated = pricingRuleService.updatePricingRule(carmodel, request);
        return ResponseEntity.ok(updated);
    }
}
