package no.dnb.exercise.sts_service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class DelegatedTokenService {

    private final KeyService keyService;

    public DelegatedTokenService(KeyService keyService) {
        this.keyService = keyService;
    }

    public String issueDelegatedAccessToken(
            String subject,
            String audience,
            String scope,
            String actor
    ) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(300);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("sts-service")
                    .subject(subject)
                    .audience(audience)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .claim("scope", scope)
                    .claim("act", Map.of(
                            "sub", actor
                    ))
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(keyService.getPrivateKey().getKeyID())
                            .build(),
                    claims
            );

            jwt.sign(new RSASSASigner(keyService.getPrivateKey()));

            return jwt.serialize();

        } catch (JOSEException e) {
            throw new IllegalStateException("Could not issue delegated access token", e);
        }
    }
}