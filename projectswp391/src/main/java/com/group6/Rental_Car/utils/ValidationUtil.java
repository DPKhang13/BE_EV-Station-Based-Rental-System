package com.group6.Rental_Car.utils;

import com.group6.Rental_Car.dtos.feedback.FeedbackResponse;
import com.group6.Rental_Car.entities.Feedback;
import com.group6.Rental_Car.exceptions.BadRequestException;

import java.math.BigDecimal;
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
            throw new BadRequestException(message + " is required");
        }
        return obj;
    }

    public static String requireNonBlank(String obj, String message) {
        if (obj == null || obj.isBlank()) {
            throw new BadRequestException(message + " is required");
        }
        return obj;
    }

    public static void ensureMaxLength(String obj, int max, String message) {
        if (obj == null || obj.length() > max) {
            throw new BadRequestException(message + " length must be <= " + max);
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
        return (t == null) ? null : t.toLowerCase();
    }

    public static String normalizeVariant(String s) {
        String t = trim(s);
        if (t == null || t.isEmpty()) return null;

        // First letter uppercase + rest lowercase
        return t.substring(0, 1).toUpperCase() + t.substring(1).toLowerCase();
    }

    public static String validateVariantBySeatCount(Integer seatCount, String rawVariant) {
        if (seatCount == null) {
            throw new BadRequestException("seatCount must be 4 or 7 (required)");
        }
        if (seatCount != 4 && seatCount != 7) {
            throw new BadRequestException("seatCount must be 4 or 7");
        }

        String variant = normalizeVariant(rawVariant);

        if (seatCount == 4) {
            if (variant == null) {
                throw new BadRequestException("variant must be one of: air|pro|plus when seatCount = 4");
            }
            if (!variant.equalsIgnoreCase("air") && !variant.equalsIgnoreCase("pro") && !variant.equalsIgnoreCase("plus")) {
                throw new BadRequestException("variant must be one of: air|pro|plus when seatCount = 4");
            }
            return variant;
        } else { // seatCount == 7
            if (variant == null) {
                throw new BadRequestException("variant is required when seatCount = 7");
            }
            if (!variant.equalsIgnoreCase("air") && !variant.equalsIgnoreCase("pro") && !variant.equalsIgnoreCase("plus")) {
                throw new BadRequestException("variant must be one of: air|pro|plus when seatCount = 7");
            }
            return variant;
        }
    }


    public static void ensureNonNegative(BigDecimal value, String field) {
        if (value == null) {
            throw new BadRequestException(field + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(field + " must be >= 0");
        }
    }

    public static void ensureNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new BadRequestException(field + " must be >= 0");
        }
    }

    public static void ensureNonNegative(Long value, String field) {
        if (value != null && value < 0L) {
            throw new BadRequestException(field + " must be >= 0");
        }
    }

    public static void ensureNonNegative(Double value, String field) {
        if (value != null && value < 0d) {
            throw new BadRequestException(field + " must be >= 0");
        }
    }
}