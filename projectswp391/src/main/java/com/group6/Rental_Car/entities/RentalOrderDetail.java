package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rentalorder_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalOrderDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Long detailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private RentalOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    // DEPOSIT | RENTAL | RETURN | SERVICE | OTHER
    @Column(name = "type")
    private String type;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "description")
    private String description;

    // pending | confirmed | active | done | cancelled
    @Column(name = "status")
    private String status;

    @PrePersist
    void prePersist() {
        if (status == null || status.isBlank()) status = "pending";
        if (price == null) price = BigDecimal.ZERO;
    }
}
