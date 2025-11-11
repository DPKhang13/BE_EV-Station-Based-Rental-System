package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.RentalOrderDetail;
import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RentalOrderDetailRepository extends JpaRepository<RentalOrderDetail, Long> {

    List<RentalOrderDetail> findByVehicle(Vehicle vehicle);

    List<RentalOrderDetail> findAllByOrder_OrderIdOrderByStartTimeDesc(UUID orderId);
    List<RentalOrderDetail> findAllByVehicle_VehicleIdOrderByStartTimeDesc(Long vehicleId);
    List<RentalOrderDetail> findAllByStatusOrderByStartTimeDesc(String status);
    List<RentalOrderDetail> findAllByTypeOrderByStartTimeDesc(String type);

    // Admin Dashboard
    List<RentalOrderDetail> findAllByOrderByStartTimeDesc();

    @Query("""
           SELECT d FROM RentalOrderDetail d
           WHERE d.vehicle.vehicleId = :vehicleId
             AND d.status IN ('confirmed','active')
             AND NOT (d.endTime <= :from OR d.startTime >= :to)
           """)
    List<RentalOrderDetail> findOverlappingForVehicle(@Param("vehicleId") Long vehicleId,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to);

    @Query(value = """
        SELECT v.station_id, COUNT(DISTINCT d.vehicle_id) AS busy
        FROM rentalorder_detail d
        JOIN vehicle v ON v.vehicle_id = d.vehicle_id
        WHERE d.status IN ('confirmed','active')
          AND NOT (d.end_time <= :from OR d.start_time >= :to)
        GROUP BY v.station_id
        ORDER BY v.station_id
        """, nativeQuery = true)
    List<Object[]> busyVehiclesPerStation(@Param("from") Timestamp from,
                                          @Param("to") Timestamp to);

    // ======= BỔ SUNG: Tổng doanh thu theo khoảng (sum price) =======
    @Query(value = """
        SELECT COALESCE(SUM(d.price),0)
        FROM rentalorder_detail d
        WHERE d.start_time BETWEEN :from AND :to
          AND d.status IN ('confirmed','active','done')
        """, nativeQuery = true)
    Double revenueBetween(@Param("from") Timestamp from,
                          @Param("to") Timestamp to);

    // ======= BỔ SUNG: Doanh thu theo ngày =======
    @Query(value = """
        SELECT DATE_TRUNC('day', d.start_time)::date AS d,
               COALESCE(SUM(d.price),0)             AS s
        FROM rentalorder_detail d
        WHERE d.start_time BETWEEN :from AND :to
          AND d.status IN ('confirmed','active','done')
        GROUP BY d
        ORDER BY d
        """, nativeQuery = true)
    List<Object[]> revenueByDay(@Param("from") Timestamp from,
                                @Param("to") Timestamp to);

    @Query(value = """
        SELECT v.station_id, rs.name,
               COALESCE(SUM(d.price),0) AS revenue
        FROM rentalorder_detail d
        JOIN vehicle v        ON v.vehicle_id = d.vehicle_id
        JOIN rentalstation rs ON rs.station_id = v.station_id
        WHERE d.start_time BETWEEN :from AND :to
          AND d.status IN ('confirmed','active','done')
        GROUP BY v.station_id, rs.name
        ORDER BY v.station_id
        """, nativeQuery = true)
    List<Object[]> revenuePerStation(@Param("from") Timestamp from,
                                     @Param("to") Timestamp to);

    @Query(value = """
        SELECT v.station_id, rs.name,
               COALESCE(SUM(d.price),0) AS revenue
        FROM rentalorder_detail d
        JOIN vehicle v        ON v.vehicle_id = d.vehicle_id
        JOIN rentalstation rs ON rs.station_id = v.station_id
        WHERE d.status IN ('confirmed','active','done')
        GROUP BY v.station_id, rs.name
        ORDER BY v.station_id
        """, nativeQuery = true)
    List<Object[]> revenuePerStationAllTime();

    @Query(value = """
        SELECT EXTRACT(HOUR FROM d.start_time)::int AS h, COUNT(*) AS c
        FROM rentalorder_detail d
        WHERE d.status IN ('confirmed','active','done')
        GROUP BY h
        ORDER BY h
        """, nativeQuery = true)
    List<Object[]> ordersByHourAllTime();

    @Query(value = """
        SELECT EXTRACT(HOUR FROM d.start_time)::int AS h, COUNT(*) AS c
        FROM rentalorder_detail d
        WHERE d.status IN ('confirmed','active','done')
          AND d.start_time BETWEEN :from AND :to
        GROUP BY h
        ORDER BY h
        """, nativeQuery = true)
    List<Object[]> ordersByHour(@Param("from") Timestamp from,
                                @Param("to") Timestamp to);
}
