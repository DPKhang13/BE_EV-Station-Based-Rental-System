package com.group6.Rental_Car.dtos.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderSimpleResponse {

    private UUID orderId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private BigDecimal totalPrice;
    private BigDecimal remainingAmount; // Tiền chưa thanh toán
    private String status;
    private String couponCode;
    
    // Thông tin khách hàng
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;

}

