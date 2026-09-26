package com.medscan.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(new KeyPairProvider());
    }

    @Test
    void generatesAndValidatesRS256TokenSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant exp = now.plus(15, ChronoUnit.MINUTES);

        JwtClaims originalClaims = new JwtClaims(
                userId,
                "doctor@medscan.org",
                "doctor@medscan.org",
                tenantId,
                Set.of("DOCTOR"),
                Set.of("consultation:write", "patient:search"),
                "https://auth.medscan.org/realms/medscan",
                now,
                exp,
                UUID.randomUUID().toString());

        String token = tokenService.generateToken(originalClaims);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);

        JwtClaims parsedClaims = tokenService.validateAndParse(token);
        assertEquals(userId, parsedClaims.subject());
        assertEquals(tenantId, parsedClaims.tenantId());
        assertEquals("doctor@medscan.org", parsedClaims.preferredUsername());
        assertTrue(parsedClaims.roles().contains("DOCTOR"));
        assertTrue(parsedClaims.permissions().contains("consultation:write"));
        assertTrue(parsedClaims.permissions().contains("patient:search"));
    }

    @Test
    void rejectsExpiredToken() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant expiredTime = Instant.now().minus(5, ChronoUnit.MINUTES);

        JwtClaims claims = new JwtClaims(
                userId,
                "patient@medscan.org",
                "patient@medscan.org",
                tenantId,
                Set.of("PATIENT"),
                Set.of("patient:read_own"),
                "https://auth.medscan.org/realms/medscan",
                Instant.now().minus(20, ChronoUnit.MINUTES),
                expiredTime,
                UUID.randomUUID().toString());

        String token = tokenService.generateToken(claims);

        assertThrows(TokenExpiredException.class, () -> tokenService.validateAndParse(token));
    }

    @Test
    void rejectsTamperedTokenPayload() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        JwtClaims claims = new JwtClaims(
                userId,
                "nurse@medscan.org",
                "nurse@medscan.org",
                tenantId,
                Set.of("NURSE"),
                Set.of("vitals:record"),
                "https://auth.medscan.org/realms/medscan",
                Instant.now(),
                Instant.now().plus(15, ChronoUnit.MINUTES),
                UUID.randomUUID().toString());

        String token = tokenService.generateToken(claims);
        String[] parts = token.split("\\.");

        // Tamper with payload (substituting second segment)
        String tamperedToken = parts[0] + ".eyJzdWIiOiJoYWNrZWQifQ." + parts[2];

        assertThrows(InvalidTokenException.class, () -> tokenService.validateAndParse(tamperedToken));
    }
}
