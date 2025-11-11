package com.group6.Rental_Car.dtos.timeline;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTimelineCreateRequest {
    private Long vehicleId;
    private UUID orderId;
    private Long detailId;
    private Long serviceId;
    private String sourceType;
    private String status;
    private String note;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
