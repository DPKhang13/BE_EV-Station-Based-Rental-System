package com.group6.Rental_Car.dtos.orderdetail;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailResponse {

    private Long detailId;
    private UUID orderId;
    private Long vehicleId;
    private String type;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private BigDecimal price;
    private String description;
    private String status;
    private String methodPayment;
    
    // Thông tin khách hàng
    private String customerName;
    private String phone;
    private String email;
    
    // Thông tin xe
    private String vehicleName;
    private String plateNumber;
    private String color;
    private String carmodel;
    private String vehicleStatus;
    
    // Thông tin trạm
    private String stationName;
    
    // Tiền còn lại chưa thanh toán
    private BigDecimal remainingAmount;
}
