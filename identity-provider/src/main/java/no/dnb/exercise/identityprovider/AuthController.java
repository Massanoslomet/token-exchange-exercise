package no.dnb.exercise.identity_provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AuthController {

    private final ClientRegistry clientRegistry;
    private final TokenService tokenService;

    public AuthController(ClientRegistry clientRegistry, TokenService tokenService) {
        this.clientRegistry = clientRegistry;
        this.tokenService = tokenService;
    }

    @PostMapping(
            value = "/auth/token",
            consumes = "application/json",
            produces = "application/json"
    )
    public ResponseEntity<?> token(@Valid @RequestBody TokenRequest request) {
        return clientRegistry.authenticate(request.client_id(), request.client_secret())
                .<ResponseEntity<?>>map(client -> ResponseEntity.ok(Map.of(
                        "id_token", tokenService.issueIdToken(client),
                        "token_type", "Bearer",
                        "expires_in", 300
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        ApiError.of(
                                "invalid_client",
                                "Invalid client_id or client_secret"
                        )
                ));
    }

    public record TokenRequest(
            @NotBlank String client_id,
            @NotBlank String client_secret
    ) {
    }
}