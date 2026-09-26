package com.medscan.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.medscan.security.jwt.JwtClaims;
import com.medscan.security.jwt.JwtTokenService;
import com.medscan.security.jwt.KeyPairProvider;

class AuthServiceTest {

    private AuthService authService;
    private JwtTokenService tokenService;
    private SeedUserRegistry registry;

    @BeforeEach
    void setUp() {
        KeyPairProvider keyPairProvider = new KeyPairProvider();
        tokenService = new JwtTokenService(keyPairProvider);
        registry = new SeedUserRegistry();
        authService = new AuthService(registry, tokenService);
    }

    @ParameterizedTest(name = "Acteur {0} (rôle {1}) s''authentifie avec succès")
    @CsvSource({
            "patient@medscan.org, PATIENT",
            "doctor@medscan.org, DOCTOR",
            "radiologist@medscan.org, RADIOLOGIST",
            "nurse@medscan.org, NURSE",
            "labtech@medscan.org, LAB_TECHNICIAN",
            "pharmacist@medscan.org, PHARMACIST",
            "delivery@medscan.org, DELIVERY_AGENT",
            "tenantadmin@medscan.org, TENANT_ADMIN",
            "superadmin@medscan.org, SUPER_ADMIN",
            "auditor@medscan.org, AUDITOR"
    })
    void allSystemActorsCanAuthenticateSuccessfully(String username, String expectedRole) {
        AuthResult result = authService.login(username, SeedUserRegistry.DEFAULT_PASSWORD);

        assertNotNull(result);
        assertNotNull(result.accessToken());
        assertNotNull(result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(900, result.expiresInSeconds());

        ActorAccount account = result.account();
        assertEquals(username, account.username());
        assertTrue(account.roles().contains(expectedRole),
                "Le compte " + username + " doit comporter le rôle " + expectedRole);

        // Validate the issued access token
        JwtClaims claims = tokenService.validateAndParse(result.accessToken());
        assertEquals(account.userId(), claims.subject());
        assertEquals(account.tenantId(), claims.tenantId());
        assertTrue(claims.roles().contains(expectedRole));
    }

    @Test
    @DisplayName("Rejet des identifiants invalides")
    void rejectsInvalidPassword() {
        assertThrows(AuthenticationFailedException.class,
                () -> authService.login("doctor@medscan.org", "WrongPassword!"));
    }

    @Test
    @DisplayName("Rejet des utilisateurs inexistants")
    void rejectsUnknownUser() {
        assertThrows(AuthenticationFailedException.class,
                () -> authService.login("unknown@medscan.org", "Password123!"));
    }

    @Test
    @DisplayName("Rafraîchissement réussi d'un jeton")
    void refreshesTokenSuccessfully() {
        AuthResult loginResult = authService.login("pharmacist@medscan.org", SeedUserRegistry.DEFAULT_PASSWORD);

        AuthResult refreshResult = authService.refreshToken(loginResult.refreshToken());
        assertNotNull(refreshResult.accessToken());

        JwtClaims claims = tokenService.validateAndParse(refreshResult.accessToken());
        assertEquals(loginResult.account().userId(), claims.subject());
        assertTrue(claims.roles().contains("PHARMACIST"));
    }
}
