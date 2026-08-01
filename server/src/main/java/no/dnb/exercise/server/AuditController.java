package no.dnb.exercise.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuditController {

    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping(
            value = "/audit",
            produces = "application/json"
    )
    public List<AuditEntry> audit() {
        return auditLogService.recentEntries();
    }
}