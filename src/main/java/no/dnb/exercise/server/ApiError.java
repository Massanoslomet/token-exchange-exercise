package no.dnb.exercise.server;

import java.time.Instant;

public record ApiError(
        String error,
        String error_description,
        Instant timestamp
) {
    public static ApiError of(String error, String description) {
        return new ApiError(error, description, Instant.now());
    }
}