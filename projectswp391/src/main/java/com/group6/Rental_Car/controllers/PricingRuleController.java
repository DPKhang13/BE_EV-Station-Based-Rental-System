package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.pricingrule.PricingRuleResponse;
import com.group6.Rental_Car.dtos.pricingrule.PricingRuleUpdateRequest;
import com.group6.Rental_Car.entities.PricingRule;
import com.group6.Rental_Car.services.pricingrule.PricingRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricingrule")
@Tag(name = "Api PricingRule",description = "Lay bang gia va them bang gia")
public class PricingRuleController {
    @Autowired
    private PricingRuleService pricingRuleService;
    @PostMapping("/update/{vehicleId}")
    public ResponseEntity updatePricingRule(@PathVariable Long vehicleId,
                                            @RequestBody PricingRuleUpdateRequest req){
        PricingRuleResponse response=pricingRuleService.updatePricingRule(vehicleId,req);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/getAll")
    public ResponseEntity<?> getAll(){
        List<PricingRuleResponse> response = pricingRuleService.getAllPricingRules();
        return ResponseEntity.ok(response);
    }
}
