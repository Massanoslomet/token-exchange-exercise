package no.dnb.exercise.sts_service;

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
public class SubjectTokenValidator {

    private final String jwksUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public SubjectTokenValidator(@Value("${idp.jwks-url}") String jwksUrl) {
        this.jwksUrl = jwksUrl;
    }

    public SubjectTokenClaims validate(String subjectToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(subjectToken);

            String keyId = signedJWT.getHeader().getKeyID();
            RSAKey publicKey = fetchPublicKey(keyId);

            JWSVerifier verifier = new RSASSAVerifier(publicKey.toRSAPublicKey());

            if (!signedJWT.verify(verifier)) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            String issuer = signedJWT.getJWTClaimsSet().getIssuer();
            if (!"identity-provider".equals(issuer)) {
                throw new IllegalArgumentException("Invalid issuer");
            }

            if (!signedJWT.getJWTClaimsSet().getAudience().contains("frontend-service")) {
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

            return new SubjectTokenClaims(subject, scope);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid subject_token", e);
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
            throw new IllegalStateException("Could not fetch JWKS from IdP");
        }

        JWKSet jwkSet = JWKSet.parse(response.body());
        JWK jwk = jwkSet.getKeyByKeyId(keyId);

        if (jwk == null) {
            throw new IllegalArgumentException("No public key found for kid: " + keyId);
        }

        return jwk.toRSAKey();
    }

    public record SubjectTokenClaims(
            String subject,
            String scope
    ) {
    }
}