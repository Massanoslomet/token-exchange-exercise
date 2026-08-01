package no.dnb.exercise.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtValidationService jwtValidationService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public TokenAuthenticationFilter(
            JwtValidationService jwtValidationService,
            ObjectMapper objectMapper,
            AuditLogService auditLogService
    ) {
        this.jwtValidationService = jwtValidationService;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/api/resources")) {
            filterChain.doFilter(request, response);
            return;
        }

        String action = request.getMethod() + " " + request.getRequestURI();
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            auditLogService.logAccess(
                    "unknown",
                    "unknown",
                    action,
                    "none",
                    "DENIED_MISSING_TOKEN"
            );

            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "unauthorized",
                    "Missing Authorization Bearer token"
            );
            return;
        }

        String token = authorization.substring("Bearer ".length());

        JwtValidationService.TokenClaims claims;

        try {
            claims = jwtValidationService.validate(token);
        } catch (IllegalArgumentException e) {
            auditLogService.logAccess(
                    "unknown",
                    "unknown",
                    action,
                    "unknown",
                    "DENIED_INVALID_TOKEN"
            );

            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "unauthorized",
                    "Invalid or expired token"
            );
            return;
        }

        String requiredScope = requiredScopeFor(request.getMethod());

        if (requiredScope != null && !hasScope(claims.scope(), requiredScope)) {
            auditLogService.logAccess(
                    claims.subject(),
                    claims.actor(),
                    action,
                    claims.scope(),
                    "DENIED_INSUFFICIENT_SCOPE"
            );

            writeError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "insufficient_scope",
                    "Token does not have required scope: " + requiredScope
            );
            return;
        }

        auditLogService.logAccess(
                claims.subject(),
                claims.actor(),
                action,
                claims.scope(),
                "ALLOWED"
        );

        request.setAttribute("subject", claims.subject());
        request.setAttribute("scope", claims.scope());
        request.setAttribute("actor", claims.actor());

        filterChain.doFilter(request, response);
    }

    private String requiredScopeFor(String method) {
        return switch (method) {
            case "GET" -> "backend:read";
            case "POST", "PUT", "DELETE" -> "backend:write";
            default -> null;
        };
    }

    private boolean hasScope(String tokenScopes, String requiredScope) {
        if (tokenScopes == null || tokenScopes.isBlank()) {
            return false;
        }

        Set<String> scopes = Arrays.stream(tokenScopes.split("\\s+"))
                .collect(Collectors.toSet());

        return scopes.contains(requiredScope);
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String error,
            String description
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");

        ApiError apiError = ApiError.of(error, description);

        objectMapper.writeValue(response.getWriter(), apiError);
    }
}