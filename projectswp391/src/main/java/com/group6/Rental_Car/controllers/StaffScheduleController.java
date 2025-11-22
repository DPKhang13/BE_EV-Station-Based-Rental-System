package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.stafflist.StaffCreateRequest;
import com.group6.Rental_Car.dtos.stafflist.StaffResponse;
import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleCreateRequest;
import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleResponse;
import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleUpdateRequest;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.services.authencation.UserService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/staffschedule")
@Tag(name = "Api StaffSchedule", description = "Create, update, search staff schedule")
public class    StaffScheduleController {

    private final StaffScheduleService staffScheduleService;
    private final UserService userService;

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
    @GetMapping("getlist/staff")
    public ResponseEntity<List<StaffResponse>> getStaffList(){
        return ResponseEntity.ok(staffScheduleService.getStaffList());
    }

    @PutMapping("/staff/{staffId}/toggle")
    public ResponseEntity<String> toggleStaffStatus(@PathVariable UUID staffId) {
        staffScheduleService.toggleStaffStatus(staffId);
        return ResponseEntity.ok("Staff status updated");
    }

    @PostMapping("/createStaff")
    public ResponseEntity<?> createStaff(@RequestBody @Valid StaffCreateRequest request) {
        User user = userService.createStaff(request);
        return ResponseEntity.ok("Tạo staff thành công với id: " + user.getUserId());
    }

    @PutMapping("/staff/update/{email}")
    public ResponseEntity<?> updateStaff(@PathVariable String email,
                                         @RequestBody com.group6.Rental_Car.dtos.stafflist.StaffUpdateRequest request) {
        return ResponseEntity.ok(userService.updateStaffByEmail(email, request));
    }

    @DeleteMapping("/deleteUser/by-email")
    public ResponseEntity<?> deleteUserByEmail(@RequestParam String email) {

        userService.deleteByEmail(email);
        return ResponseEntity.ok("User deleted successfully");
    }

}