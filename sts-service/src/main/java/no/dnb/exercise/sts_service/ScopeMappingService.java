package no.dnb.exercise.sts_service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScopeMappingService {

    private static final Map<String, String> IDP_TO_BACKEND_SCOPE = Map.of(
            "read", "backend:read",
            "write", "backend:write",
            "admin", "backend:admin"
    );

    public Set<String> allowedDelegatedScopes(String subjectScopes, String callerAllowedScopes) {
        Set<String> allowedFromSubject = splitScopes(subjectScopes).stream()
                .map(IDP_TO_BACKEND_SCOPE::get)
                .filter(scope -> scope != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> allowedFromCaller = splitScopes(callerAllowedScopes);

        allowedFromSubject.retainAll(allowedFromCaller);

        return allowedFromSubject;
    }

    public Set<String> requestedScopes(String requestedScope) {
        return splitScopes(requestedScope);
    }

    public boolean isAllowed(Set<String> requestedScopes, Set<String> allowedScopes) {
        return allowedScopes.containsAll(requestedScopes);
    }

    private Set<String> splitScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(scopes.split("\\s+"))
                .filter(scope -> !scope.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}