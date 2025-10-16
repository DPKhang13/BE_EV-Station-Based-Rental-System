package com.group6.Rental_Car.services.staffschedule;

import com.group6.Rental_Car.dtos.staffschedule.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StaffScheduleService {
    StaffScheduleResponse create(StaffScheduleCreateRequest req);
    StaffScheduleResponse update(Integer id, StaffScheduleUpdateRequest req);
    Page<StaffScheduleResponse> getAll(Pageable pageable);
    Page<StaffScheduleResponse> search(UUID userId, Integer stationId, LocalDate from, LocalDate to, String q,  Pageable pageable);
}
