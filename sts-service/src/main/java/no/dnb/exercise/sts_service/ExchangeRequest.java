package no.dnb.exercise.sts_service;

import jakarta.validation.constraints.NotBlank;

public record ExchangeRequest(
        @NotBlank String grant_type,
        @NotBlank String subject_token,
        @NotBlank String subject_token_type,
        @NotBlank String requested_token_type,
        @NotBlank String scope,
        @NotBlank String audience
) {
}