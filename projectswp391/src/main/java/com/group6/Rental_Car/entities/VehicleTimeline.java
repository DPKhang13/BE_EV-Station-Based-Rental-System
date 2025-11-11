package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor @Builder
@Entity
@Table(name = "vehicle_timeline")
public class VehicleTimeline {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "timeline_id")
    private Long timelineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    // free | booked | maintenance | service
    @Column(name = "status", length = 30, nullable = false)
    private String status;

    // ORDER_DETAIL | ORDER_SERVICE | MANUAL_BLOCK
    @Column(name = "source_type", length = 20)
    private String sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private RentalOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detail_id")
    private RentalOrderDetail detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private OrderService service;

    @Column(name = "note")
    private String note; // SOFT_HOLD | HARD_LOCK | lý do khác

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }
}
