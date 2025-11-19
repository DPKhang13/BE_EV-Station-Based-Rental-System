package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.RentalOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RentalOrderRepository extends JpaRepository<RentalOrder, UUID> {

    // Lấy danh sách đơn hàng của khách hàng
    List<RentalOrder> findByCustomer_UserId(UUID customerId);
    @Query("""
    SELECT DISTINCT o
    FROM RentalOrder o
    JOIN o.details d
    WHERE d.vehicle.vehicleId = :vehicleId
""")
    List<RentalOrder> findOrdersByVehicleId(Long vehicleId);
    // Lấy theo trạng thái
    @EntityGraph(attributePaths = {"customer"})
    List<RentalOrder> findByStatus(String status);

    List<RentalOrder> findByStatusIn(List<String> statuses);

    List<RentalOrder> findByCustomer_UserIdOrderByCreatedAtDesc(UUID customerId);

    // Đếm số order theo trạng thái (cho dashboard)
    long countByStatus(String status);

    // =============================
    // DOANH THU THEO ORDER DETAIL
    // =============================

    // Tổng doanh thu trong khoảng thời gian
    @Query(value = """
        SELECT COALESCE(SUM(rod.price), 0)
        FROM rentalorder_detail rod
        JOIN rentalorder ro ON rod.order_id = ro.order_id
        WHERE rod.start_time BETWEEN :from AND :to
          AND UPPER(ro.status) IN ('RENTAL', 'COMPLETED', 'RETURN', 'ACTIVE')
    """, nativeQuery = true)
    Double revenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Doanh thu theo ngày
    @Query(value = """
        SELECT DATE_TRUNC('day', rod.start_time)::date AS day,
               COALESCE(SUM(rod.price), 0) AS total
        FROM rentalorder_detail rod
        JOIN rentalorder ro ON rod.order_id = ro.order_id
        WHERE rod.start_time BETWEEN :from AND :to
          AND UPPER(ro.status) IN ('RENTAL', 'COMPLETED', 'RETURN', 'ACTIVE')
        GROUP BY day
        ORDER BY day
    """, nativeQuery = true)
    List<Object[]> revenueByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Doanh thu theo trạm (dựa vào vehicle)
    @Query(value = """
        SELECT s.station_id,
               s.name AS station_name,
               COALESCE(SUM(rod.price), 0) AS total
        FROM rentalstation s
        LEFT JOIN vehicle v ON v.station_id = s.station_id
        LEFT JOIN rentalorder_detail rod ON rod.vehicle_id = v.vehicle_id
        LEFT JOIN rentalorder ro ON rod.order_id = ro.order_id
             AND rod.start_time BETWEEN :from AND :to
             AND UPPER(ro.status) IN ('RENTAL', 'COMPLETED', 'RETURN', 'ACTIVE')
        GROUP BY s.station_id, s.name
        ORDER BY total DESC
    """, nativeQuery = true)
    List<Object[]> revenuePerStation(@Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    // Doanh thu hôm nay
    @Query(value = """
        SELECT s.station_id,
               s.name AS station_name,
               COALESCE(SUM(rod.price), 0) AS total
        FROM rentalstation s
        LEFT JOIN vehicle v ON v.station_id = s.station_id
        LEFT JOIN rentalorder_detail rod ON rod.vehicle_id = v.vehicle_id
        LEFT JOIN rentalorder ro ON rod.order_id = ro.order_id
             AND DATE(rod.start_time) = CURRENT_DATE
             AND UPPER(ro.status) IN ('RENTAL', 'COMPLETED', 'RETURN', 'ACTIVE')
        GROUP BY s.station_id, s.name
    """, nativeQuery = true)
    List<Object[]> revenueTodayPerStation();

    // Doanh thu tuần này
    @Query(value = """
        SELECT s.station_id,
               s.name AS station_name,
               COALESCE(SUM(rod.price), 0) AS total
        FROM rentalstation s
        LEFT JOIN vehicle v ON v.station_id = s.station_id
        LEFT JOIN rentalorder_detail rod ON rod.vehicle_id = v.vehicle_id
        LEFT JOIN rentalorder ro ON rod.order_id = ro.order_id
             AND rod.start_time >= DATE_TRUNC('week', CURRENT_DATE)
             AND UPPER(ro.status) IN ('RENTAL', 'COMPLETED', 'RETURN', 'ACTIVE')
        GROUP BY s.station_id, s.name
    """, nativeQuery = true)
    List<Object[]> revenueThisWeekPerStation();

    // Doanh thu tháng này
    @Query(value = """
        SELECT s.station_id,
               s.name AS station_name,
               COALESCE(SUM(rod.price), 0) AS total
        FROM rentalstation s
        LEFT JOIN vehicle v ON v.station_id = s.station_id
        LEFT JOIN rentalorder_detail rod ON rod.vehicle_id = v.vehicle_id
        LEFT JOIN rentalorder ro ON rod.order_id = ro.order_id
             AND rod.start_time >= DATE_TRUNC('month', CURRENT_DATE)
             AND UPPER(ro.status) IN ('RENTAL', 'COMPLETED', 'RETURN', 'ACTIVE')
        GROUP BY s.station_id, s.name
    """, nativeQuery = true)
    List<Object[]> revenueThisMonthPerStation();

    // Đếm order theo giờ trong ngày
    @Query(value = """
        SELECT EXTRACT(HOUR FROM rod.start_time) AS hour,
               COUNT(DISTINCT ro.order_id) AS count
        FROM rentalorder_detail rod
        JOIN rentalorder ro ON rod.order_id = ro.order_id
        WHERE rod.start_time BETWEEN :from AND :to
        GROUP BY hour
        ORDER BY hour
    """, nativeQuery = true)
    List<Object[]> countOrdersByHour(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Doanh thu của 1 station trong khoảng thời gian
    @Query(value = """
        SELECT COALESCE(SUM(rod.price), 0)
        FROM rentalorder_detail rod
        JOIN rentalorder ro ON rod.order_id = ro.order_id
        JOIN vehicle v ON rod.vehicle_id = v.vehicle_id
        WHERE v.station_id = :stationId
          AND rod.start_time BETWEEN :from AND :to
          AND UPPER(ro.status) IN ('RENTAL', 'COMPLETED', 'RETURN', 'ACTIVE')
    """, nativeQuery = true)
    Double revenueByStationBetween(@Param("stationId") Integer stationId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}
