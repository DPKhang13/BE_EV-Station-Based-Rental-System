package com.group6.Rental_Car.entities;

import com.group6.Rental_Car.enums.IncidentSeverity;
import com.group6.Rental_Car.enums.IncidentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_id")
    private Integer incidentId;

    @Column(name = "station_id")
    private Integer stationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private IncidentStatus status;

    @Column(name = "incident_type", length = 50)
    private String incidentType;

    @Column(name = "occurred_on")
    private LocalDate occurredOn;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "cost", precision = 18, scale = 2)
    private BigDecimal cost;

    @Column(name = "reported_by", length = 100)
    private UUID reportedBy;

    @PrePersist
    void prePersist() {
        if (reportedAt == null) reportedAt = LocalDateTime.now();
        if (occurredOn == null) occurredOn = LocalDate.now();
        if (incidentType == null || incidentType.isBlank()) incidentType = "maintenance";
    }
}
