package no.dnb.exercise.server;

import java.time.Instant;

public record AuditEntry(
        Instant timestamp,
        String event,
        String user,
        String actor,
        String action,
        String scope,
        String result
) {
}