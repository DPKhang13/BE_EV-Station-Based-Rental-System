package com.group6.Rental_Car.dtos.rentalorderdetail;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalOrderDetailResponse {

    private Long detailId;

    private UUID orderId;
    private String orderStatus;        // tiện ích xem nhanh

    private Long vehicleId;
    private String plateNumber;
    private String vehicleName;

    private String type;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal price;
    private String description;
    private String status;
}
