package no.dnb.exercise.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final List<AuditEntry> entries = new ArrayList<>();

    public synchronized void logAccess(
            String user,
            String actor,
            String action,
            String scope,
            String result
    ) {
        AuditEntry entry = new AuditEntry(
                Instant.now(),
                "ACCESS",
                user,
                actor,
                action,
                scope,
                result
        );

        entries.add(entry);

        if (entries.size() > 100) {
            entries.remove(0);
        }

        log.info(
                "{} | {} | user={} | actor={} | action={} | scope={} | result={}",
                entry.timestamp(),
                entry.event(),
                entry.user(),
                entry.actor(),
                entry.action(),
                entry.scope(),
                entry.result()
        );
    }

    public synchronized List<AuditEntry> recentEntries() {
        List<AuditEntry> copy = new ArrayList<>(entries);
        Collections.reverse(copy);
        return copy;
    }
}