package com.group6.Rental_Car.dtos.feedback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackCreateRequest {
    private UUID orderId;
    private Integer rating;  // 1..5
    private String comment;  // <=255

}
