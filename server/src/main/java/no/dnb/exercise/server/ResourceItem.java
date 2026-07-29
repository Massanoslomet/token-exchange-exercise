package no.dnb.exercise.server;

public record ResourceItem(
        int id,
        String name,
        String owner
) {
}