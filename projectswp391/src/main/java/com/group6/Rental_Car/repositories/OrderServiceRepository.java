package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.OrderService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderServiceRepository extends JpaRepository<OrderService, Long> {

    //  Lấy tất cả dịch vụ theo đơn thuê
    List<OrderService> findByOrder_OrderId(UUID orderId);

    //  Lấy tất cả dịch vụ theo chi tiết đơn thuê (nếu có)
    List<OrderService> findByDetail_DetailId(Long detailId);

    //  Lấy tất cả dịch vụ theo xe
    List<OrderService> findByVehicle_VehicleId(Long vehicleId);

    //  Lấy tất cả dịch vụ theo trạm
    List<OrderService> findByStation_StationId(Integer stationId);

    //  Lọc theo trạng thái (pending | processing | done | cancelled)
    List<OrderService> findByStatusIgnoreCase(String status);

    //  Lọc theo loại dịch vụ (MAINTENANCE | CLEANING | REPAIR | INCIDENT | OTHER)
    List<OrderService> findByServiceTypeIgnoreCase(String serviceType);

    @Query("""
        SELECT SUM(s.cost)
        FROM OrderService s
        WHERE s.occurredAt BETWEEN :from AND :to
    """)
    Double totalCostBetween(@Param("from") Timestamp from, @Param("to") Timestamp to);

    //  Lấy tất cả dịch vụ phát sinh trong khoảng ngày (dùng cho dashboard)
    @Query("""
        SELECT s
        FROM OrderService s
        WHERE DATE(s.occurredAt) BETWEEN :from AND :to
        ORDER BY s.occurredAt DESC
    """)
    List<OrderService> findAllInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    //  Lấy top 10 service gần nhất (cho recent list)
    List<OrderService> findTop10ByOrderByOccurredAtDesc();
    @Query(value = """
    SELECT DATE_TRUNC('day', s.occurredAt)::date AS day,
           COUNT(*) AS total
    FROM orderservice s
    WHERE s.occurredAt BETWEEN :from AND :to
    GROUP BY day
    ORDER BY day
""", nativeQuery = true)
    List<Object[]> countByDay(@Param("from") java.sql.Timestamp from,
                              @Param("to") java.sql.Timestamp to);

}

