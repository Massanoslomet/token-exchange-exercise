package no.dnb.exercise.identity_provider;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class TokenService {

    private final KeyService keyService;

    public TokenService(KeyService keyService) {
        this.keyService = keyService;
    }

    public String issueIdToken(ClientRegistry.ClientInfo client) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(300);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("identity-provider")
                    .subject(client.clientId())
                    .audience("frontend-service")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .claim("scope", client.scope())
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyService.getPrivateKey().getKeyID())
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);

            RSASSASigner signer = new RSASSASigner(keyService.getPrivateKey());
            signedJWT.sign(signer);

            return signedJWT.serialize();

        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign JWT", e);
        }
    }
}