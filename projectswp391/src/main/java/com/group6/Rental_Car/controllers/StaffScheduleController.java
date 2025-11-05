package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleCreateRequest;
import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleResponse;
import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleUpdateRequest;
import com.group6.Rental_Car.services.staffschedule.StaffScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;               // <— quan trọng
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;                  // <— set default page/size/sort
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/staffschedule")
@Tag(name = "Api StaffSchedule", description = "Create, update, search staff schedule")
public class    StaffScheduleController {

    private final StaffScheduleService staffScheduleService;

    @PostMapping("/create")
    public ResponseEntity<StaffScheduleResponse> create(@Valid @RequestBody StaffScheduleCreateRequest req) {
        return ResponseEntity.ok(staffScheduleService.create(req));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<StaffScheduleResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody StaffScheduleUpdateRequest req
    ) {
        return ResponseEntity.ok(staffScheduleService.update(id, req));
    }

    @GetMapping("/list")
    public ResponseEntity<Page<StaffScheduleResponse>> list(
            @ParameterObject
            @PageableDefault(size = 10, sort = "shiftDate", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(staffScheduleService.getAll(pageable));
    }


}
