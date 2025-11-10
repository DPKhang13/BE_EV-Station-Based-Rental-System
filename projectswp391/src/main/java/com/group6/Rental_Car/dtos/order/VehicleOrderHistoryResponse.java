package com.group6.Rental_Car.dtos.order;

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
public class VehicleOrderHistoryResponse {

    private UUID orderId;
    private Long vehicleId;
    private String plateNumber;

    private Integer stationId;
    private String stationName;

    private String brand;
    private String color;
    private String transmission;
    private Integer seatCount;
    private Integer year;
    private String variant;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private String status;
    private BigDecimal totalPrice;
}
