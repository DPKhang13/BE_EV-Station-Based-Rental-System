package com.group6.Rental_Car.dtos.orderservice;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderServiceResponse {
    private Integer serviceId;
    private Long vehicleId;
    private String plateNumber;
    private Integer stationId;
    private String serviceType;
    private String description;
    private BigDecimal cost;
    private UUID performedBy;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private String status;
    private String note;

    private UUID orderId;
    private Long detailId;
}
