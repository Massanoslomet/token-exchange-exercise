package no.dnb.exercise.server;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtValidationService {

    private final String jwksUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public JwtValidationService(@Value("${sts.jwks-url}") String jwksUrl) {
        this.jwksUrl = jwksUrl;
    }

    public TokenClaims validate(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            String keyId = signedJWT.getHeader().getKeyID();
            RSAKey publicKey = fetchPublicKey(keyId);

            JWSVerifier verifier = new RSASSAVerifier(publicKey.toRSAPublicKey());

            if (!signedJWT.verify(verifier)) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            String issuer = signedJWT.getJWTClaimsSet().getIssuer();
            if (!"sts-service".equals(issuer)) {
                throw new IllegalArgumentException("Invalid issuer");
            }

            if (!signedJWT.getJWTClaimsSet().getAudience().contains("backend-service")) {
                throw new IllegalArgumentException("Invalid audience");
            }

            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationTime == null || expirationTime.toInstant().isBefore(Instant.now())) {
                throw new IllegalArgumentException("Token has expired");
            }

            String subject = signedJWT.getJWTClaimsSet().getSubject();
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("Missing subject");
            }

            String scope = signedJWT.getJWTClaimsSet().getStringClaim("scope");
            if (scope == null || scope.isBlank()) {
                throw new IllegalArgumentException("Missing scope");
            }

            String actor = null;
            Object actClaim = signedJWT.getJWTClaimsSet().getClaim("act");

            if (actClaim instanceof java.util.Map<?, ?> actMap) {
                Object actorSubject = actMap.get("sub");
                if (actorSubject != null) {
                    actor = actorSubject.toString();
                }
            }

            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("Missing actor claim");
            }

            return new TokenClaims(subject, scope, actor);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired token", e);
        }
    }

    private RSAKey fetchPublicKey(String keyId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUrl))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Could not fetch JWKS from STS");
        }

        JWKSet jwkSet = JWKSet.parse(response.body());
        JWK jwk = jwkSet.getKeyByKeyId(keyId);

        if (jwk == null) {
            throw new IllegalArgumentException("No public key found for kid: " + keyId);
        }

        return jwk.toRSAKey();
    }

    public record TokenClaims(
            String subject,
            String scope,
            String actor
    ) {
    }
}