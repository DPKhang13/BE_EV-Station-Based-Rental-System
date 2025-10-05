package com.group6.Rental_Car.entities;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "[RetalOrder]") // 'User' là keyword trong SQL Server nên để trong []
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalOrder {
// nếu bảng bạn tên là order thì đổi lại thành "order"

        @Id
        @Column(name = "order_id", nullable = false)
        private UUID orderId;

        @Column(name = "customer_id")
        private UUID customerId;

        @Column(name = "vehicle_id")
        private Long vehicleId;

        @Column(name = "start_time")
        private LocalDateTime startTime;

        @Column(name = "end_time")
        private LocalDateTime endTime;

        @Column(name = "total_price")
        private Double totalPrice;

        @Column(name = "status")
        private String status;

        // Getter & Setter
    }

