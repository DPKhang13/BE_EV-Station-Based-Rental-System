package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_timeline")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "timeline_id")
    private Long timelineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private RentalOrder order; // Đơn thuê liên quan (có thể null nếu là service)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detail_id")
    private RentalOrderDetail detail; // Chi tiết thuê

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private OrderService service; // Service liên quan (maintenance, cleaning...)

    @Column(nullable = false)
    private LocalDate day;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(length = 30)
    private String status; // BOOKED | IN_USE | MAINTENANCE | AVAILABLE | RETURNED ...

    @Column(name = "source_type", length = 20)
    private String sourceType; // ORDER | DETAIL | SERVICE

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "updated_at", nullable = false, updatable = false)
    private LocalDateTime updatedAt;
}
