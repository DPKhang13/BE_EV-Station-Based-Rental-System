package com.group6.Rental_Car.dtos.feedback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackUpdateRequest {
    private String comment;  // optional, <=255
}
