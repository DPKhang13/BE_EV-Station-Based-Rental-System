package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.EmployeeSchedule;
import com.group6.Rental_Car.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Integer> {

    boolean existsByStaff_UserIdAndShiftDateAndShiftTime(
            UUID userId, LocalDate shiftDate, String shiftTime);

    boolean existsByStaff_UserIdAndShiftDateAndShiftTimeAndScheduleIdNot(
            UUID userId, LocalDate shiftDate, String shiftTime, Integer scheduleId);


    // Lấy theo staff + ngày + ca
    Optional<EmployeeSchedule> findByStaff_UserIdAndShiftDateAndShiftTime(
            UUID userId, LocalDate shiftDate, String shiftTime);

    // Có phân trang (tùy service kết hợp điều kiện lọc)
    Page<EmployeeSchedule> findAll(Pageable pageable);

    void deleteByStaff_UserId(UUID userId);
    Optional<EmployeeSchedule> findByScheduleId(Integer scheduleId);
}