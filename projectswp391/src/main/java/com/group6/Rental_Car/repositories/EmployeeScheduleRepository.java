package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.dtos.stafflist.staffList;
import com.group6.Rental_Car.entities.EmployeeSchedule;
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
    Optional<EmployeeSchedule> findByStaff_UserIdAndShiftDateAndShiftTime(
            UUID userId, LocalDate shiftDate, String shiftTime);

    @Query(value = """
    SELECT 
      u.user_id   AS staffId,
      u.full_name AS staffName,
      u.email     AS staffEmail,
      u.role      AS role,
      rsu.name    AS stationName,                               -- lấy theo user.station_id
      COALESCE(SUM(es.pickup_count), 0)  AS pickupCount,        -- tách pickup
      COALESCE(SUM(es.return_count), 0)  AS returnCount,        -- tách return
      u.status    AS status
    FROM "user" u
    LEFT JOIN rentalstation   rsu ON rsu.station_id = u.station_id
    LEFT JOIN employeeschedule es  ON es.staff_id    = u.user_id
    WHERE u.role = 'staff'
    GROUP BY u.user_id, u.full_name, u.email, u.role, rsu.name, u.status
    ORDER BY u.full_name ASC
    """, nativeQuery = true)
    List<staffList> getStaffList();

}
