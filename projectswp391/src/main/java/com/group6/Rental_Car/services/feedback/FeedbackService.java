package com.group6.Rental_Car.services.feedback;

import com.group6.Rental_Car.dtos.feedback.FeedbackCreateRequest;
import com.group6.Rental_Car.dtos.feedback.FeedbackResponse;
import com.group6.Rental_Car.dtos.feedback.FeedbackUpdateRequest;

import java.util.List;

public interface FeedbackService {
    FeedbackResponse create(FeedbackCreateRequest req);

    FeedbackResponse update(Integer feedbackId, FeedbackUpdateRequest req);

    void delete(Integer feedbackId);

    FeedbackResponse getById(Integer feedbackId);

}