package no.dnb.exercise.frontend_service;

public record BackendResponse(
        int statusCode,
        String body
) {}