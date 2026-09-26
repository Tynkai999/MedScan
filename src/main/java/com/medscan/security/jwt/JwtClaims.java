package com.medscan.security.jwt;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Representation of JWT claims verified or embedded in MedScan access tokens.
 * Conforms to Keycloak OIDC claim conventions without exposing sensitive clinical data.
 */
public record JwtClaims(
        UUID subject,
        String preferredUsername,
        String email,
        UUID tenantId,
        Set<String> roles,
        Set<String> permissions,
        String issuer,
        Instant issuedAt,
        Instant expiresAt,
        String jwtId) {

    public JwtClaims {
        Objects.requireNonNull(subject, "subject (userId) must not be null.");
        Objects.requireNonNull(tenantId, "tenantId must not be null.");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null.");

        preferredUsername = (preferredUsername == null || preferredUsername.isBlank())
                ? subject.toString()
                : preferredUsername.trim();
        email = (email == null) ? "" : email.trim();
        roles = (roles == null) ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(roles));
        permissions = (permissions == null) ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(permissions));
        issuer = (issuer == null || issuer.isBlank()) ? "medscan-auth-server" : issuer.trim();
        issuedAt = (issuedAt == null) ? Instant.now() : issuedAt;
        jwtId = (jwtId == null || jwtId.isBlank()) ? UUID.randomUUID().toString() : jwtId;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
