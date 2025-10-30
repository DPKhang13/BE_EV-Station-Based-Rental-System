package com.group6.Rental_Car.dtos.verifyfile;


import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVerificationResponse {
    private String orderId;
    private String customerName;
    private String phone;
    private String vehicleName;
    private String plateNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal totalPrice;
    private BigDecimal depositAmount;
    private String status;
}