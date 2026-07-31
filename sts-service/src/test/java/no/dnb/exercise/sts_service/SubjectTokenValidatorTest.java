package no.dnb.exercise.sts_service;


import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class SubjectTokenValidatorTest {

    private static HttpServer jwksServer;
    private static RSAKey rsaKey;
    private static String jwksUrl;

    @BeforeAll
    static void startJwksServer() throws Exception {
        rsaKey = generateRsaKey();

        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);

        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            String body = new JWKSet(rsaKey.toPublicJWK()).toString();

            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);

            try (var outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });

        jwksServer.start();

        jwksUrl = "http://localhost:"
                + jwksServer.getAddress().getPort()
                + "/.well-known/jwks.json";
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void shouldValidateValidSubjectToken() throws Exception {
        SubjectTokenValidator validator = new SubjectTokenValidator(jwksUrl);

        String token = issueToken(
                "identity-provider",
                "agent_alpha",
                "frontend-service",
                "read",
                Instant.now().plusSeconds(300)
        );

        SubjectTokenValidator.SubjectTokenClaims claims = validator.validate(token);

        assertEquals("agent_alpha", claims.subject());
        assertEquals("read", claims.scope());
    }

    @Test
    void shouldRejectFakeToken() {
        SubjectTokenValidator validator = new SubjectTokenValidator(jwksUrl);

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("fake-token")
        );
    }

    @Test
    void shouldRejectWrongIssuer() throws Exception {
        SubjectTokenValidator validator = new SubjectTokenValidator(jwksUrl);

        String token = issueToken(
                "wrong-issuer",
                "agent_alpha",
                "frontend-service",
                "read",
                Instant.now().plusSeconds(300)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(token)
        );
    }

    @Test
    void shouldRejectWrongAudience() throws Exception {
        SubjectTokenValidator validator = new SubjectTokenValidator(jwksUrl);

        String token = issueToken(
                "identity-provider",
                "agent_alpha",
                "wrong-audience",
                "read",
                Instant.now().plusSeconds(300)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(token)
        );
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        SubjectTokenValidator validator = new SubjectTokenValidator(jwksUrl);

        String token = issueToken(
                "identity-provider",
                "agent_alpha",
                "frontend-service",
                "read",
                Instant.now().minusSeconds(60)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(token)
        );
    }

    private static String issueToken(
            String issuer,
            String subject,
            String audience,
            String scope,
            Instant expiresAt
    ) throws JOSEException {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("scope", scope)
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(rsaKey.getKeyID())
                        .build(),
                claims
        );

        jwt.sign(new RSASSASigner(rsaKey));

        return jwt.serialize();
    }

    private static RSAKey generateRsaKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        KeyPair keyPair = generator.generateKeyPair();

        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("idp-key-2026")
                .build();
    }
}
