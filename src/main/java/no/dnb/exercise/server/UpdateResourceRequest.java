package no.dnb.exercise.server;

import jakarta.validation.constraints.NotBlank;

public record UpdateResourceRequest(
        @NotBlank String name,
        @NotBlank String owner
) {
}