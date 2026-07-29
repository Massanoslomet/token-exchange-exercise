package no.dnb.exercise.identity_provider;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    @Test
    void shouldIssueRs256SignedJwtWithExpectedClaims() throws ParseException {
        KeyService keyService = new KeyService();
        TokenService tokenService = new TokenService(keyService);

        ClientRegistry.ClientInfo client = new ClientRegistry.ClientInfo(
                "agent_alpha",
                "alpha-secret",
                "read"
        );

        String token = tokenService.issueIdToken(client);

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);

        SignedJWT signedJWT = SignedJWT.parse(token);

        assertEquals(JWSAlgorithm.RS256, signedJWT.getHeader().getAlgorithm());
        assertEquals("idp-key-2026", signedJWT.getHeader().getKeyID());

        assertEquals("identity-provider", signedJWT.getJWTClaimsSet().getIssuer());
        assertEquals("agent_alpha", signedJWT.getJWTClaimsSet().getSubject());
        assertEquals(List.of("frontend-service"), signedJWT.getJWTClaimsSet().getAudience());
        assertEquals("read", signedJWT.getJWTClaimsSet().getStringClaim("scope"));

        assertNotNull(signedJWT.getJWTClaimsSet().getIssueTime());
        assertNotNull(signedJWT.getJWTClaimsSet().getExpirationTime());
        assertTrue(
                signedJWT.getJWTClaimsSet()
                        .getExpirationTime()
                        .after(signedJWT.getJWTClaimsSet().getIssueTime())
        );
    }

    @Test
    void shouldPublishPublicJwksWithoutPrivateKeyMaterial() {
        KeyService keyService = new KeyService();

        Map<String, Object> jwks = keyService.getPublicJwks();

        assertTrue(jwks.containsKey("keys"));

        List<?> keys = (List<?>) jwks.get("keys");
        assertEquals(1, keys.size());

        Map<?, ?> key = (Map<?, ?>) keys.get(0);

        assertEquals("RSA", key.get("kty"));
        assertEquals("idp-key-2026", key.get("kid"));
        assertEquals("sig", key.get("use"));
        assertEquals("RS256", key.get("alg"));

        assertTrue(key.containsKey("n"));
        assertTrue(key.containsKey("e"));

        assertFalse(key.containsKey("d"));
        assertFalse(key.containsKey("p"));
        assertFalse(key.containsKey("q"));
        assertFalse(key.containsKey("dp"));
        assertFalse(key.containsKey("dq"));
        assertFalse(key.containsKey("qi"));
    }
}
