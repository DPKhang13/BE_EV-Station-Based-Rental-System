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
public class OrderResponse {

    private UUID orderId;
    private Long vehicleId;
    private String plateNumber; // Biển số xe
    private String brand; // Hãng xe
    private String carmodel; // Car model

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private BigDecimal totalPrice;
    private BigDecimal remainingAmount; // Tiền chưa thanh toán
    private String status;
    private String couponCode;

    private Integer stationId;
    private String stationName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualReturnTime;// số ngày thuê thực tế (nếu người dùng muốn override)

}
