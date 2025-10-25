package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.EmployeeSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Integer> {

    boolean existsByStaff_UserIdAndShiftDateAndShiftTime(
            UUID userId, LocalDate shiftDate, String shiftTime);

    boolean existsByStaff_UserIdAndShiftDateAndShiftTimeAndScheduleIdNot(
            UUID userId, LocalDate shiftDate, String shiftTime, Integer scheduleId);


    @Query("""
      select s
      from EmployeeSchedule s
      where (:userId   is null or s.staff.userId = :userId)
        and (:stationId is null or s.station.stationId = :stationId)
        and (:fromDate is null or s.shiftDate >= :fromDate)
        and (:toDate   is null or s.shiftDate <= :toDate)
        and (:q is null or lower(s.shiftTime) like lower(concat('%', :q, '%')))
      """)
    Page<EmployeeSchedule> search(@Param("userId") UUID userId,
                                  @Param("stationId") Integer stationId,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  @Param("q") String q,
                                  Pageable pageable);
}