package com.group6.Rental_Car.dtos.feedback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeedbackResponse {
    private Integer feedbackId;
    private UUID orderId;
    private Integer rating;
    private String comment;
}
