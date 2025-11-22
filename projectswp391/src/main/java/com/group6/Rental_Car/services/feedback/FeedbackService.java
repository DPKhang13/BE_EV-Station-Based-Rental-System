package com.group6.Rental_Car.services.feedback;

import com.group6.Rental_Car.dtos.feedback.FeedbackCreateRequest;
import com.group6.Rental_Car.dtos.feedback.FeedbackResponse;
import com.group6.Rental_Car.dtos.feedback.FeedbackUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface FeedbackService {
    FeedbackResponse create(FeedbackCreateRequest req);
    FeedbackResponse update(Integer feedbackId, FeedbackUpdateRequest req);
    void delete(Integer feedbackId);
    List<FeedbackResponse> getByOrderId(UUID orderId);
    List<FeedbackResponse> list();
}