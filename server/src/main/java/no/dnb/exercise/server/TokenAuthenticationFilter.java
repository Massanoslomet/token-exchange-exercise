package no.dnb.exercise.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtValidationService jwtValidationService;
    private final ObjectMapper objectMapper;

    public TokenAuthenticationFilter(
            JwtValidationService jwtValidationService,
            ObjectMapper objectMapper
    ) {
        this.jwtValidationService = jwtValidationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/resources")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing Authorization Bearer token");
            return;
        }

        String token = authorizationHeader.substring("Bearer ".length());

        try {
            JwtValidationService.TokenClaims claims = jwtValidationService.validate(token);

            request.setAttribute("user", claims.subject());
            request.setAttribute("scope", claims.scope());

            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException e) {
            writeUnauthorized(response, "Invalid or expired token");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String description) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError error = ApiError.of(
                "unauthorized",
                description
        );

        objectMapper.writeValue(response.getWriter(), error);
    }
}