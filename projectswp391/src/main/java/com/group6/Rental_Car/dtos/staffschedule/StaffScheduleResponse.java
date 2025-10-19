package com.group6.Rental_Car.dtos.staffschedule;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class StaffScheduleResponse {

    private Integer scheduleId;

    private UUID staffId;
    private String staffName;

    private Integer stationId;
    private String stationName;

    private LocalDate shiftDate;
    private String shiftTime;
}
