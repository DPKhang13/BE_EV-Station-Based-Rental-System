package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.feedback.FeedbackCreateRequest;
import com.group6.Rental_Car.dtos.feedback.FeedbackResponse;
import com.group6.Rental_Car.dtos.feedback.FeedbackUpdateRequest;
import com.group6.Rental_Car.services.feedback.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {
    private final FeedbackService feedbackService;
    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/create")
    public ResponseEntity<FeedbackResponse> create(@RequestBody FeedbackCreateRequest req) {
        return ResponseEntity.ok(feedbackService.create(req));
    }

    @PutMapping("/update")
    public ResponseEntity<FeedbackResponse> update(@PathVariable Integer feedbackId,
                                                   @RequestBody FeedbackUpdateRequest req) {
        return ResponseEntity.ok(feedbackService.update(feedbackId, req));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(@PathVariable Integer feedbackId) {
        feedbackService.delete(feedbackId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/getById")
    public ResponseEntity<FeedbackResponse> getById(@PathVariable Integer feedbackId) {
        return ResponseEntity.ok(feedbackService.getById(feedbackId));
    }

    @GetMapping("/getAllList")
    public ResponseEntity<List<FeedbackResponse>> list() {
        return ResponseEntity.ok(feedbackService.list());
    }
}
