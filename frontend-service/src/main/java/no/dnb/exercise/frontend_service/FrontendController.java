package no.dnb.exercise.frontend_service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class FrontendController {

    private final FrontendOrchestrationService orchestrationService;

    public FrontendController(FrontendOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @GetMapping(value = "/frontend/resources", produces = "application/json")
    public ResponseEntity<String> getResources(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            ApiError error = ApiError.of("unauthorized", "Missing Authorization Bearer token");
            return ResponseEntity.status(401)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"error":"%s","error_description":"%s","timestamp":"%s"}
                            """.formatted(error.error(), error.error_description(), error.timestamp()));
        }

        String idToken = authorization.substring("Bearer ".length());
        BackendResponse response = orchestrationService.getResources(idToken);

        return ResponseEntity.status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    @ExceptionHandler(FrontendException.class)
    public ResponseEntity<String> handleFrontendException(FrontendException e) {
        return ResponseEntity.status(e.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(e.responseBody());
    }
}