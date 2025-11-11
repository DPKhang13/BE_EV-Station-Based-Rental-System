package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "orderservice")
public class OrderService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Integer serviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private RentalOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detail_id")
    private RentalOrderDetail detail;

    // === vehicle & station ===
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "station_id")
    private Integer stationId;

    // === các cột đúng tên trong orderservice ===
    @Column(name = "service_type", length = 100, nullable = false)
    private String serviceType;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // pending | processing | done | cancelled
    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "note")
    private String note;

    @PrePersist
    void prePersist() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
        if (serviceType == null || serviceType.isBlank()) serviceType = "MAINTENANCE";
        if (status == null || status.isBlank()) status = "pending";
    }
}
