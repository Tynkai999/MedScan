package com.medscan.security.jwt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Provides RSA keys for signing and verifying JWT tokens (RS256).
 * In production, keys are verified against the Keycloak realm JWKS endpoint.
 */
@ApplicationScoped
public class KeyPairProvider {

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String keyId;

    public KeyPairProvider() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.keyId = "medscan-jwt-key-1";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm is not available in the runtime environment", e);
        }
    }

    public KeyPairProvider(RSAPublicKey publicKey, RSAPrivateKey privateKey, String keyId) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
        this.keyId = (keyId == null || keyId.isBlank()) ? "medscan-jwt-key-1" : keyId;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getKeyId() {
        return keyId;
    }
}
