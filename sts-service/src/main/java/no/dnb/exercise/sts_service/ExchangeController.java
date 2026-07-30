package no.dnb.exercise.sts_service;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ExchangeController {

    private static final String TOKEN_EXCHANGE_GRANT_TYPE =
            "urn:ietf:params:oauth:grant-type:token-exchange";

    private static final String ID_TOKEN_TYPE =
            "urn:ietf:params:oauth:token-type:id_token";

    private static final String ACCESS_TOKEN_TYPE =
            "urn:ietf:params:oauth:token-type:access_token";

    private final ServiceRegistry serviceRegistry;
    private final SubjectTokenValidator subjectTokenValidator;

    public ExchangeController(
            ServiceRegistry serviceRegistry,
            SubjectTokenValidator subjectTokenValidator
    ) {
        this.serviceRegistry = serviceRegistry;
        this.subjectTokenValidator = subjectTokenValidator;
    }

    @PostMapping(
            value = "/exchange",
            consumes = "application/json",
            produces = "application/json"
    )
    public ResponseEntity<?> exchange(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ExchangeRequest request
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiError.of(
                            "invalid_client",
                            "Missing frontend service credential"
                    )
            );
        }

        String callerToken = authorization.substring("Bearer ".length());

        ServiceRegistry.ServiceClient caller = serviceRegistry.authenticate(callerToken)
                .orElse(null);

        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiError.of(
                            "invalid_client",
                            "Unknown or invalid frontend service credential"
                    )
            );
        }

        if (!TOKEN_EXCHANGE_GRANT_TYPE.equals(request.grant_type())) {
            return ResponseEntity.badRequest().body(
                    ApiError.of(
                            "invalid_request",
                            "Unsupported grant_type"
                    )
            );
        }

        if (!ID_TOKEN_TYPE.equals(request.subject_token_type())) {
            return ResponseEntity.badRequest().body(
                    ApiError.of(
                            "invalid_request",
                            "Unsupported subject_token_type"
                    )
            );
        }

        if (!ACCESS_TOKEN_TYPE.equals(request.requested_token_type())) {
            return ResponseEntity.badRequest().body(
                    ApiError.of(
                            "invalid_request",
                            "Unsupported requested_token_type"
                    )
            );
        }

        if (!"backend-service".equals(request.audience())) {
            return ResponseEntity.badRequest().body(
                    ApiError.of(
                            "invalid_target",
                            "Unsupported audience"
                    )
            );
        }

        SubjectTokenValidator.SubjectTokenClaims subjectClaims;

        try {
            subjectClaims = subjectTokenValidator.validate(request.subject_token());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiError.of(
                            "invalid_grant",
                            "Invalid, expired, or tampered subject_token"
                    )
            );
        }

        return ResponseEntity.ok(Map.of(
                "access_token", "temporary-delegated-token-for-" + subjectClaims.subject(),
                "token_type", "Bearer",
                "expires_in", 300,
                "issued_token_type", ACCESS_TOKEN_TYPE,
                "scope", request.scope()
        ));
    }
}