package com.group6.Rental_Car.dtos.feedback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackUpdateRequest {
    private Integer rating;  // optional, 1..5
    private String comment;  // optional, <=255
}
