package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public interface RentalOrderRepository extends JpaRepository<RentalOrder, UUID> {
    List<RentalOrder> findByCustomer_UserIdOrderByCreatedAtDesc(UUID customerId);
    @EntityGraph(attributePaths = {"customer", "vehicle"})
    List<RentalOrder> findByStatus(String status);
    List<RentalOrder> findByStatusIn(List<String> statuses);


    //Admin Dashboard
    long countByStatus(String status);
    // Tổng doanh thu (status đã ghi nhận doanh thu)
    @Query(value = """
        SELECT COALESCE(SUM(total_price),0)
        FROM rentalorder
        WHERE start_time BETWEEN :from AND :to
          AND status IN ('active','completed','done','paid')
        """, nativeQuery = true)
    Double revenueBetween(@Param("from") Timestamp from, @Param("to") Timestamp to);

    // Doanh thu theo ngày
    @Query(value = """
        SELECT DATE_TRUNC('day', start_time)::date AS d,
               COALESCE(SUM(total_price),0)       AS s
        FROM rentalorder
        WHERE start_time BETWEEN :from AND :to
          AND status IN ('active','completed','done','paid')
        GROUP BY d
        ORDER BY d
        """, nativeQuery = true)
    List<Object[]> revenueByDay(@Param("from") Timestamp from, @Param("to") Timestamp to);
}
