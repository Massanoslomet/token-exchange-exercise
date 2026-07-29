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
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtValidationService {

    private final String jwksUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public JwtValidationService(@Value("${idp.jwks-url}") String jwksUrl) {
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
            String scope = signedJWT.getJWTClaimsSet().getStringClaim("scope");

            return new TokenClaims(subject, scope);

        } catch (Exception e) {
            throw new IllegalArgumentException("Token validation failed: " + e.getMessage(), e);
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

    public record TokenClaims(
            String subject,
            String scope
    ) {
    }
}