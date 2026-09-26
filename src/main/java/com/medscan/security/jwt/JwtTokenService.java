package com.medscan.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service responsible for RS256 JWT token generation, signature verification,
 * and claims parsing.
 */
@ApplicationScoped
public class JwtTokenService {

    private final KeyPairProvider keyPairProvider;

    @Inject
    public JwtTokenService(KeyPairProvider keyPairProvider) {
        this.keyPairProvider = Objects.requireNonNull(keyPairProvider, "KeyPairProvider must not be null.");
    }

    public JwtTokenService() {
        this(new KeyPairProvider());
    }

    /**
     * Generates a signed RS256 JWT token from the given claims.
     */
    public String generateToken(JwtClaims claims) {
        Objects.requireNonNull(claims, "claims must not be null.");

        String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\""
                + JsonHelper.escape(keyPairProvider.getKeyId()) + "\"}";

        StringBuilder payload = new StringBuilder();
        payload.append("{");
        payload.append("\"sub\":\"").append(claims.subject()).append("\",");
        payload.append("\"preferred_username\":\"").append(JsonHelper.escape(claims.preferredUsername())).append("\",");
        payload.append("\"email\":\"").append(JsonHelper.escape(claims.email())).append("\",");
        payload.append("\"tenant_id\":\"").append(claims.tenantId()).append("\",");

        // Keycloak standard realm_access
        payload.append("\"realm_access\":{\"roles\":[");
        payload.append(claims.roles().stream()
                .map(r -> "\"" + JsonHelper.escape(r) + "\"")
                .collect(Collectors.joining(",")));
        payload.append("]},");

        // MedScan fine-grained permissions
        payload.append("\"permissions\":[");
        payload.append(claims.permissions().stream()
                .map(p -> "\"" + JsonHelper.escape(p) + "\"")
                .collect(Collectors.joining(",")));
        payload.append("],");

        payload.append("\"iss\":\"").append(JsonHelper.escape(claims.issuer())).append("\",");
        payload.append("\"iat\":").append(claims.issuedAt().getEpochSecond()).append(",");
        payload.append("\"exp\":").append(claims.expiresAt().getEpochSecond()).append(",");
        payload.append("\"jti\":\"").append(JsonHelper.escape(claims.jwtId())).append("\"");
        payload.append("}");

        String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = base64UrlEncode(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;

        byte[] signatureBytes = sign(signingInput.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = base64UrlEncode(signatureBytes);

        return signingInput + "." + encodedSignature;
    }

    /**
     * Validates signature and expiration, and extracts claims from a raw JWT token string.
     */
    public JwtClaims validateAndParse(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            throw new InvalidTokenException("Token string is null or blank.");
        }

        String[] parts = tokenString.trim().split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("Invalid JWT format. Expected 3 segments separated by dots.");
        }

        String encodedHeader = parts[0];
        String encodedPayload = parts[1];
        String encodedSignature = parts[2];

        String signingInput = encodedHeader + "." + encodedPayload;
        byte[] signatureBytes = base64UrlDecode(encodedSignature);

        if (!verify(signingInput.getBytes(StandardCharsets.UTF_8), signatureBytes)) {
            throw new InvalidTokenException("JWT signature verification failed.");
        }

        String payloadJson = new String(base64UrlDecode(encodedPayload), StandardCharsets.UTF_8);

        String subStr = JsonHelper.getString(payloadJson, "sub");
        String tenantIdStr = JsonHelper.getString(payloadJson, "tenant_id");
        Long expSec = JsonHelper.getLong(payloadJson, "exp");

        if (subStr == null || tenantIdStr == null || expSec == null) {
            throw new InvalidTokenException("Token payload missing required claims (sub, tenant_id, or exp).");
        }

        UUID subject;
        UUID tenantId;
        try {
            subject = UUID.fromString(subStr);
            tenantId = UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid UUID format in token claims.", e);
        }

        Instant expiresAt = Instant.ofEpochSecond(expSec);
        if (Instant.now().isAfter(expiresAt)) {
            throw new TokenExpiredException("Token expired at " + expiresAt);
        }

        String preferredUsername = JsonHelper.getString(payloadJson, "preferred_username");
        String email = JsonHelper.getString(payloadJson, "email");
        String issuer = JsonHelper.getString(payloadJson, "iss");
        String jwtId = JsonHelper.getString(payloadJson, "jti");
        Long iatSec = JsonHelper.getLong(payloadJson, "iat");
        Instant issuedAt = (iatSec != null) ? Instant.ofEpochSecond(iatSec) : Instant.now();

        Set<String> roles = JsonHelper.getStringArray(payloadJson, "roles");
        Set<String> permissions = JsonHelper.getStringArray(payloadJson, "permissions");

        return new JwtClaims(
                subject,
                preferredUsername,
                email,
                tenantId,
                roles,
                permissions,
                issuer,
                issuedAt,
                expiresAt,
                jwtId);
    }

    private byte[] sign(byte[] data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPairProvider.getPrivateKey());
            signature.update(data);
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Failed to sign JWT with RS256", e);
        }
    }

    private boolean verify(byte[] data, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(keyPairProvider.getPublicKey());
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            return false;
        }
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String str) {
        try {
            return Base64.getUrlDecoder().decode(str);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid Base64URL encoding in token.", e);
        }
    }
}
