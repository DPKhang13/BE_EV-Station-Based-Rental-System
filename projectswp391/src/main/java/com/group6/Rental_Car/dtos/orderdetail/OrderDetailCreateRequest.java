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
public class OrderDetailCreateRequest {

    private UUID orderId;          // đơn lớn
    private Long vehicleId;        // xe được thuê / đổi
    private String type;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")// DEPOSIT | RENTAL | RETURN | SERVICE | OTHER
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private BigDecimal price;
    private String description;
}
