package com.medscan.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.security.MedscanPrincipal;
import com.medscan.security.MedscanSecurityContext;
import com.medscan.security.jwt.JwtTokenService;
import com.medscan.security.jwt.KeyPairProvider;
import com.medscan.security.service.AuthService;
import com.medscan.security.service.SeedUserRegistry;
import com.medscan.security.tenant.TenantContext;
import com.medscan.security.tenant.VerifiedTenantIdentity;
import jakarta.ws.rs.core.Response;

class AuthResourceTest {

    private AuthResource authResource;
    private AuthService authService;
    private SeedUserRegistry registry;
    private TenantContext tenantContext;

    @BeforeEach
    void setUp() {
        registry = new SeedUserRegistry();
        JwtTokenService tokenService = new JwtTokenService(new KeyPairProvider());
        authService = new AuthService(registry, tokenService);
        tenantContext = new TenantContext();
        authResource = new AuthResource(authService, registry, tenantContext);
    }

    @Test
    @DisplayName("Endpoint POST /api/v1/auth/login authentifie un médecin et retourne 200")
    void loginReturnsSuccessWithTokens() {
        LoginRequest request = new LoginRequest("doctor@medscan.org", SeedUserRegistry.DEFAULT_PASSWORD);

        Response response = authResource.login(request);

        assertEquals(200, response.getStatus());
        assertTrue(response.getEntity() instanceof LoginResponse);
        LoginResponse loginResponse = (LoginResponse) response.getEntity();
        assertNotNull(loginResponse.accessToken());
        assertNotNull(loginResponse.refreshToken());
        assertEquals("Bearer", loginResponse.tokenType());
        assertEquals("doctor@medscan.org", loginResponse.user().username());
        assertTrue(loginResponse.user().roles().contains("DOCTOR"));
    }

    @Test
    @DisplayName("Endpoint POST /api/v1/auth/login rejette un mauvais mot de passe avec 401")
    void loginRejectsWrongPassword() {
        LoginRequest request = new LoginRequest("doctor@medscan.org", "InvalidSecret!");

        Response response = authResource.login(request);

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("Endpoint GET /api/v1/auth/me renvoie le profil de l'utilisateur authentifié")
    void getCurrentUserReturnsProfile() {
        VerifiedTenantIdentity identity = new VerifiedTenantIdentity(
                SeedUserRegistry.TENANT_HOSPITAL,
                UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0"),
                "doctor@medscan.org",
                Set.of("DOCTOR"),
                Set.of("patient:search", "consultation:write"));
        MedscanSecurityContext securityContext = new MedscanSecurityContext(new MedscanPrincipal(identity), true);

        Response response = authResource.getCurrentUser(securityContext);

        assertEquals(200, response.getStatus());
        assertTrue(response.getEntity() instanceof UserProfileResponse);
        UserProfileResponse profile = (UserProfileResponse) response.getEntity();
        assertEquals("doctor@medscan.org", profile.username());
        assertTrue(profile.roles().contains("DOCTOR"));
    }
}
