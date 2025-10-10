package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Integer feedbackId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private RentalOrder order;   // giả định đã có entity RentalOrder @Id UUID orderId

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", length = 255)
    private String comment;
}
