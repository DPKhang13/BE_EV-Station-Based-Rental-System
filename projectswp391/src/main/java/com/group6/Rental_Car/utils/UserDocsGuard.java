package com.group6.Rental_Car.utils;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.function.BiFunction;

public final class UserDocsGuard {
    private UserDocsGuard() {}

    public static void assertHasDocs(UUID userId,
                                     BiFunction<UUID, String, Boolean> existsByType) {

        boolean hasCCCD = Boolean.TRUE.equals(existsByType.apply(userId, "CCCD"));
        boolean hasGPLX = Boolean.TRUE.equals(existsByType.apply(userId, "GPLX"));

        if (!hasCCCD || !hasGPLX) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,"You have to update verify docs in edit profile before booking!"
            );
        }
    }
}
