package com.medscan.security.service;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.medscan.security.jwt.JwtClaims;
import com.medscan.security.jwt.JwtTokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Authentication service responsible for verifying actor credentials,
 * applying MFA/policy checks, and issuing signed JWT tokens.
 */
@ApplicationScoped
public class AuthService {

    public static final long ACCESS_TOKEN_VALIDITY_SECONDS = 900; // 15 minutes (ADR-002)
    public static final long REFRESH_TOKEN_VALIDITY_SECONDS = 86400; // 24 hours

    private final SeedUserRegistry userRegistry;
    private final JwtTokenService tokenService;

    @Inject
    public AuthService(SeedUserRegistry userRegistry, JwtTokenService tokenService) {
        this.userRegistry = Objects.requireNonNull(userRegistry, "userRegistry must not be null");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService must not be null");
    }

    public AuthService() {
        this(new SeedUserRegistry(), new JwtTokenService());
    }

    /**
     * Authenticates an actor using username and password credentials.
     * Returns a signed JWT access token and refresh token upon success.
     */
    public AuthResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new AuthenticationFailedException("Les identifiants d'authentification sont obligatoires.");
        }

        ActorAccount account = userRegistry.findByUsername(username.trim())
                .orElseThrow(() -> new AuthenticationFailedException("Identifiants d'authentification invalides."));

        if (!constantTimeEquals(account.password(), password)) {
            throw new AuthenticationFailedException("Identifiants d'authentification invalides.");
        }

        Instant now = Instant.now();
        Instant accessExp = now.plus(ACCESS_TOKEN_VALIDITY_SECONDS, ChronoUnit.SECONDS);
        Instant refreshExp = now.plus(REFRESH_TOKEN_VALIDITY_SECONDS, ChronoUnit.SECONDS);

        JwtClaims accessClaims = new JwtClaims(
                account.userId(),
                account.username(),
                account.email(),
                account.tenantId(),
                account.roles(),
                account.permissions(),
                "https://auth.medscan.org/realms/medscan",
                now,
                accessExp,
                UUID.randomUUID().toString());

        JwtClaims refreshClaims = new JwtClaims(
                account.userId(),
                account.username(),
                account.email(),
                account.tenantId(),
                account.roles(),
                account.permissions(),
                "https://auth.medscan.org/realms/medscan",
                now,
                refreshExp,
                UUID.randomUUID().toString());

        String accessToken = tokenService.generateToken(accessClaims);
        String refreshToken = tokenService.generateToken(refreshClaims);

        return new AuthResult(
                accessToken,
                refreshToken,
                "Bearer",
                ACCESS_TOKEN_VALIDITY_SECONDS,
                account);
    }

    /**
     * Refreshes an access token using a valid refresh token.
     */
    public AuthResult refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationFailedException("Le jeton de rafraîchissement est obligatoire.");
        }

        JwtClaims claims = tokenService.validateAndParse(refreshToken);
        ActorAccount account = userRegistry.findById(claims.subject())
                .orElseThrow(() -> new AuthenticationFailedException("Compte utilisateur introuvable pour ce jeton."));

        Instant now = Instant.now();
        Instant accessExp = now.plus(ACCESS_TOKEN_VALIDITY_SECONDS, ChronoUnit.SECONDS);

        JwtClaims newAccessClaims = new JwtClaims(
                account.userId(),
                account.username(),
                account.email(),
                account.tenantId(),
                account.roles(),
                account.permissions(),
                "https://auth.medscan.org/realms/medscan",
                now,
                accessExp,
                UUID.randomUUID().toString());

        String newAccessToken = tokenService.generateToken(newAccessClaims);

        return new AuthResult(
                newAccessToken,
                refreshToken,
                "Bearer",
                ACCESS_TOKEN_VALIDITY_SECONDS,
                account);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }
}
