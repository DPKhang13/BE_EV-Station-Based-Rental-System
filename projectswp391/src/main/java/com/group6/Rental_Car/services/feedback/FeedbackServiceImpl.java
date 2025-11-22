package com.group6.Rental_Car.services.feedback;

import com.group6.Rental_Car.dtos.feedback.FeedbackCreateRequest;
import com.group6.Rental_Car.dtos.feedback.FeedbackResponse;
import com.group6.Rental_Car.dtos.feedback.FeedbackUpdateRequest;
import com.group6.Rental_Car.entities.Feedback;
import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.exceptions.ConflictException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.FeedbackRepository;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.group6.Rental_Car.utils.ValidationUtil.*;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService{
    private final FeedbackRepository feedbackRepository;
    private final RentalOrderRepository rentalOrderRepository;
    private final ModelMapper modelMapper;

    @Override
    public FeedbackResponse create(FeedbackCreateRequest req) {
        UUID orderId = requireNonNull(req.getOrderId(), "orderId");
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // 1 order chỉ có 1 feedback
        if (feedbackRepository.existsByOrder(order)) {
            throw new ConflictException("Feedback already exists for order: " + orderId);
        }

        Integer rating = requireNonNull(req.getRating(), "rating");
        ensureRange(rating, 1, 5, "rating");

        String comment = trim(req.getComment());
        ensureMaxLength(comment, 255, "comment");

        Feedback fb = new Feedback();
        fb.setOrder(order);
        fb.setRating(rating);
        fb.setComment(comment);

        fb = feedbackRepository.save(fb);
        return toResponse(fb);
    }

    // UPDATE (partial)
    @Override
    public FeedbackResponse update(Integer feedbackId, FeedbackUpdateRequest req) {
        Feedback fb = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found: " + feedbackId));

        if (req.getComment() != null) {
            String comment = trim(req.getComment());
            ensureMaxLength(comment, 255, "comment");
            fb.setComment(comment);
        }

        fb = feedbackRepository.save(fb);
        return toResponse(fb);
    }

    @Override
    @Transactional
    public List<FeedbackResponse> getByOrderId(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        
        List<Feedback> feedbacks = feedbackRepository.findAllByOrder(order);
        return feedbacks.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Integer feedbackId) {
        Feedback fb = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found: " + feedbackId));
        feedbackRepository.delete(fb);
    }
    @Override
    public List<FeedbackResponse> list() {
        return feedbackRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    private FeedbackResponse toResponse(Feedback fb) {
        FeedbackResponse res = modelMapper.map(fb, FeedbackResponse.class);
        if (fb.getOrder() != null) {
            res.setOrderId(fb.getOrder().getOrderId());
        }
        return res;
    }
}
