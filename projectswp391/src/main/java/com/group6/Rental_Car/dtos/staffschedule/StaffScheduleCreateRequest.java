package com.group6.Rental_Car.dtos.staffschedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class StaffScheduleCreateRequest {
    private UUID userId;
    private Integer stationId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate shiftDate;
    private String shiftTime;
}
