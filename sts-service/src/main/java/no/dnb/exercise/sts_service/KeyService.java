package no.dnb.exercise.sts_service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

@Service
public class KeyService {

    private final RSAKey rsaKey;

    public KeyService() {
        this.rsaKey = generateRsaKey();
    }

    public RSAKey getPrivateKey() {
        return rsaKey;
    }

    public Map<String, Object> getPublicJwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }

    private RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);

            KeyPair keyPair = generator.generateKeyPair();

            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID("sts-key-2026")
                    .build();

        } catch (Exception e) {
            throw new IllegalStateException("Could not generate STS RSA key pair", e);
        }
    }
}