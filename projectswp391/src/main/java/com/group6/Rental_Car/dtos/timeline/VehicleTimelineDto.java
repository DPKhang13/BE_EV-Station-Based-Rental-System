package com.group6.Rental_Car.dtos.timeline;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTimelineDto {
    private Long timelineId;
    private Long vehicleId;
    private String vehicleName;
    private UUID orderId;
    private Long detailId;
    private Long serviceId;
    private String sourceType;
    private String status;
    private String note;
    private LocalDate day;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime updatedAt;
}
