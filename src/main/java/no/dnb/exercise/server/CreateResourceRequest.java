package no.dnb.exercise.server;

import jakarta.validation.constraints.NotBlank;

public record CreateResourceRequest(
        @NotBlank String name,
        @NotBlank String owner
) {
}