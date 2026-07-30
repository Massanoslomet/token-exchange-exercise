package no.dnb.exercise.sts_service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final KeyService keyService;

    public JwksController(KeyService keyService) {
        this.keyService = keyService;
    }

    @GetMapping(
            value = "/.well-known/jwks.json",
            produces = "application/json"
    )
    public Map<String, Object> jwks() {
        return keyService.getPublicJwks();
    }
}