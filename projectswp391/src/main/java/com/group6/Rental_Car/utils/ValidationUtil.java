package com.group6.Rental_Car.utils;

import com.group6.Rental_Car.dtos.feedback.FeedbackResponse;
import com.group6.Rental_Car.entities.Feedback;
import com.group6.Rental_Car.exceptions.BadRequestException;

import java.util.Set;

public class ValidationUtil {
    private ValidationUtil() {
    }

    public static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new BadRequestException(message + "is required");
        }
        return obj;
    }

    public static String requireNonBlank(String obj, String message) {
        if (obj == null || obj.isBlank()) {
            throw new BadRequestException(message + "is required");
        }
        return obj;
    }

    public static void ensureMaxLength(String obj, int max, String message) {
        if (obj == null || obj.length() > max) {
            throw new BadRequestException(message + "length must be <=" + max);
        }
    }

    public static void ensureRange(int val, int min, int max, String message) {
        if (val < min || val > max) {
            throw new BadRequestException(message + "value must be between " + min + " and " + max);
        }
    }

    public static void ensureInSetIgnoreCase(String obj, Set<String> allowed, String message) {
        if (obj == null) return;
        if (!allowed.contains(obj.toLowerCase())) {
            throw new BadRequestException(message + "must be one of" + allowed);
        }
    }

    public static String normalizeNullableLower(String s) {
        String t = trim(s);
        return (t == null) ? null : t.toLowerCase(); //??
    }

    public static String validateVariantBySeatCount(Integer seatCount, String rawVariant) {
        if (seatCount == null) {
            throw new BadRequestException("seatCount must be 4 or 7 (required)");
        }
        if (seatCount != 4 && seatCount != 7) {
            throw new BadRequestException("seatCount must be 4 or 7");
        }

        String variant = normalizeNullableLower(rawVariant);

        if (seatCount == 4) {
            if (variant != null) {
                throw new BadRequestException("variant must be null when seatCount = 4");
            }
            return null;
        } else { // seatCount == 7
            if (variant == null) {
                throw new BadRequestException("variant is required when seatCount = 7");
            }
            if (!variant.equals("air") && !variant.equals("pro") && !variant.equals("plus")) {
                throw new BadRequestException("variant must be one of: air|pro|plus when seatCount = 7");
            }
            return variant;
        }
    }

    public static FeedbackResponse toResponse(Feedback fb) {
        if (fb.getOrder() == null) {
            throw new BadRequestException("Feedback has no order associated"); // defensive
        }
        FeedbackResponse dto = new FeedbackResponse();
        dto.setFeedbackId(fb.getFeedbackId());
        dto.setOrderId(fb.getOrder().getOrderId());
        dto.setRating(fb.getRating());
        dto.setComment(fb.getComment());
        return dto;
    }
}