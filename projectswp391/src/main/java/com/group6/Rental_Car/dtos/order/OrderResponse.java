package com.group6.Rental_Car.dtos.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrderResponse {
    private UUID orderId;
    private Long vehicleId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    private BigDecimal totalPrice;
    private String status;
    private String couponCode;
    private Integer plannedHours;
    private Integer actualHours;
    private BigDecimal penaltyFee;
    private BigDecimal depositAmount;
    private BigDecimal remainingAmount;
}
