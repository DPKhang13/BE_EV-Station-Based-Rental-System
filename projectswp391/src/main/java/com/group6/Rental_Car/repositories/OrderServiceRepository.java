package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.OrderService;
import com.group6.Rental_Car.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderServiceRepository extends JpaRepository<OrderService, Integer> {

    List<OrderService> findByVehicle(Vehicle vehicle);

    // Sort mới nhất theo occurred_at
    List<OrderService> findAllByOrderByOccurredAtDesc();

    // Lọc theo loại dịch vụ (ví dụ chỉ lấy INCIDENT)
    List<OrderService> findByServiceTypeOrderByOccurredAtDesc(String serviceType);

    // Trong khoảng thời gian (JPQL) với tham số LocalDateTime (giữ lại nếu bạn cần)
    @Query("SELECT i FROM OrderService i " +
            "WHERE (:serviceType IS NULL OR i.serviceType = :serviceType) " +
            "AND i.occurredAt BETWEEN :from AND :to " +
            "ORDER BY i.occurredAt DESC")
    List<OrderService> findInRange(@Param("serviceType") String serviceType,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    // ======= THÊM: All-in-range theo LocalDate (khớp AdminDashboardServiceImpl) =======
    @Query(value = """
        SELECT * FROM orderservice
        WHERE occurred_at::date BETWEEN :from AND :to
        ORDER BY occurred_at DESC
        """, nativeQuery = true)
    List<OrderService> findAllInRange(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    // ======= THÊM: Tổng chi phí (không filter serviceType) =======
    @Query(value = """
        SELECT COALESCE(SUM(cost),0)
        FROM orderservice
        WHERE occurred_at BETWEEN :from AND :to
        """, nativeQuery = true)
    Double totalCostBetweenTs(@Param("from") Timestamp from,
                              @Param("to") Timestamp to);

    // (Giữ lại biến thể có filter serviceType nếu bạn còn dùng ở nơi khác)
    @Query(value = """
        SELECT COALESCE(SUM(cost),0)
        FROM orderservice
        WHERE occurred_at BETWEEN :from AND :to
          AND (:serviceType IS NULL OR service_type = :serviceType)
        """, nativeQuery = true)
    Double totalCostBetweenTs(@Param("from") Timestamp from,
                              @Param("to") Timestamp to,
                              @Param("serviceType") String serviceType);

    // ======= THÊM: Đếm service theo ngày (không filter) =======
    @Query(value = """
        SELECT DATE_TRUNC('day', occurred_at)::date AS d, COUNT(*) AS c
        FROM orderservice
        WHERE occurred_at BETWEEN :from AND :to
        GROUP BY d
        ORDER BY d
        """, nativeQuery = true)
    List<Object[]> incidentsByDay(@Param("from") Timestamp from,
                                  @Param("to") Timestamp to);

    // (Giữ lại biến thể có filter type nếu cần)
    @Query(value = """
        SELECT occurred_at::date AS d, COUNT(*) AS c
        FROM orderservice
        WHERE occurred_at BETWEEN :from AND :to
          AND (:serviceType IS NULL OR service_type = :serviceType)
        GROUP BY d
        ORDER BY d
        """, nativeQuery = true)
    List<Object[]> countByDay(@Param("from") Timestamp from,
                              @Param("to") Timestamp to,
                              @Param("serviceType") String serviceType);

    // ======= THÊM: Recent services cho dashboard (khớp tên recentIncidents) =======
    @Query(value = """
        SELECT i.service_id, i.vehicle_id, v.vehicle_name,
               i.description, i.status, i.occurred_at, i.cost
        FROM orderservice i
        JOIN vehicle v ON v.vehicle_id = i.vehicle_id
        WHERE i.occurred_at BETWEEN :from AND :to
        ORDER BY i.occurred_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> recentIncidents(@Param("from") Timestamp from,
                                   @Param("to") Timestamp to,
                                   @Param("limit") int limit);

    // (Giữ lại biến thể recent có filter type nếu còn dùng nơi khác)
    @Query(value = """
        SELECT i.service_id, i.vehicle_id, v.vehicle_name,
               i.description, i.service_type, i.status, i.occurred_at, i.cost
        FROM orderservice i
        JOIN vehicle v ON v.vehicle_id = i.vehicle_id
        WHERE (:serviceType IS NULL OR i.service_type = :serviceType)
        ORDER BY i.occurred_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> recentServices(@Param("serviceType") String serviceType,
                                  @Param("limit") int limit);
}
