package com.medscan.security.service;

import java.util.Objects;

/**
 * Result of a successful authentication attempt.
 */
public record AuthResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        ActorAccount account) {

    public AuthResult {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        Objects.requireNonNull(account, "account must not be null");
        tokenType = (tokenType == null) ? "Bearer" : tokenType;
    }
}
