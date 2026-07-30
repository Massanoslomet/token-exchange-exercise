package no.dnb.exercise.sts_service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class ServiceRegistry {

    private final Map<String, ServiceClient> services = Map.of(
            "frontend-service-token",
            new ServiceClient("frontend-service", "backend:read backend:write")
    );

    public Optional<ServiceClient> authenticate(String bearerToken) {
        return Optional.ofNullable(services.get(bearerToken));
    }

    public record ServiceClient(
            String clientId,
            String allowedScopes
    ) {
    }
}