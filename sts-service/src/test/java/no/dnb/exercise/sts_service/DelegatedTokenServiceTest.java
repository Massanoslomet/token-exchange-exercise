package no.dnb.exercise.sts_service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DelegatedTokenServiceTest {

    @Test
    void shouldIssueDelegatedAccessTokenWithExpectedClaims() throws Exception {
        KeyService keyService = new KeyService();
        DelegatedTokenService delegatedTokenService = new DelegatedTokenService(keyService);

        String token = delegatedTokenService.issueDelegatedAccessToken(
                "agent_alpha",
                "backend-service",
                "backend:read",
                "frontend-service"
        );

        SignedJWT jwt = SignedJWT.parse(token);

        assertEquals(JWSAlgorithm.RS256, jwt.getHeader().getAlgorithm());
        assertEquals("sts-key-2026", jwt.getHeader().getKeyID());

        assertTrue(jwt.verify(
                new com.nimbusds.jose.crypto.RSASSAVerifier(
                        keyService.getPrivateKey().toPublicJWK().toRSAKey().toRSAPublicKey()
                )
        ));

        assertEquals("sts-service", jwt.getJWTClaimsSet().getIssuer());
        assertEquals("agent_alpha", jwt.getJWTClaimsSet().getSubject());
        assertTrue(jwt.getJWTClaimsSet().getAudience().contains("backend-service"));
        assertEquals("backend:read", jwt.getJWTClaimsSet().getStringClaim("scope"));

        Object actClaim = jwt.getJWTClaimsSet().getClaim("act");
        assertInstanceOf(Map.class, actClaim);

        Map<?, ?> act = (Map<?, ?>) actClaim;
        assertEquals("frontend-service", act.get("sub"));

        assertNotNull(jwt.getJWTClaimsSet().getIssueTime());
        assertNotNull(jwt.getJWTClaimsSet().getExpirationTime());

        assertTrue(
                jwt.getJWTClaimsSet().getExpirationTime().toInstant().isAfter(Instant.now()),
                "Token should not be expired"
        );
    }
}