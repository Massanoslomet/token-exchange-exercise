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
public class ContentNegotiationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public ContentNegotiationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);

        if (acceptHeader != null
                && !acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE)
                && !acceptHeader.contains(MediaType.ALL_VALUE)) {

            response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiError error = ApiError.of(
                    "not_acceptable",
                    "Only application/json is supported"
            );

            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }
}