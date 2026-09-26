package com.medscan.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.security.MedscanPrincipal;
import com.medscan.security.jwt.JwtTokenService;
import com.medscan.security.jwt.KeyPairProvider;
import com.medscan.security.service.AuthResult;
import com.medscan.security.service.AuthService;
import com.medscan.security.service.SeedUserRegistry;
import com.medscan.security.tenant.TenantContext;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private AuthService authService;
    private TenantContext tenantContext;

    @BeforeEach
    void setUp() {
        TestRuntimeDelegate.install();
        KeyPairProvider keyPairProvider = new KeyPairProvider();
        JwtTokenService tokenService = new JwtTokenService(keyPairProvider);
        SeedUserRegistry registry = new SeedUserRegistry();
        authService = new AuthService(registry, tokenService);
        tenantContext = new TenantContext();
        filter = new JwtAuthenticationFilter(tokenService, tenantContext, null);
    }

    @Test
    @DisplayName("L'accès aux endpoints publics ne requiert pas de jeton")
    void allowsPublicEndpointsWithoutToken() throws IOException {
        FakeContainerRequestContext context = new FakeContainerRequestContext("/v1/health");

        filter.filter(context);

        assertNull(context.getAbortedWith());
    }

    @Test
    @DisplayName("L'accès à un endpoint protégé sans jeton retourne 401")
    void rejectsMissingTokenOnProtectedEndpoint() throws IOException {
        FakeContainerRequestContext context = new FakeContainerRequestContext("/v1/portal/doctor");

        filter.filter(context);

        assertNotNull(context.getAbortedWith());
        assertEquals(401, context.getAbortedWith().getStatus());
    }

    @Test
    @DisplayName("L'accès avec un jeton invalide ou corrompu retourne 401")
    void rejectsInvalidToken() throws IOException {
        FakeContainerRequestContext context = new FakeContainerRequestContext("/v1/portal/doctor");
        context.setHeader("Authorization", "Bearer invalid.token.payload");

        filter.filter(context);

        assertNotNull(context.getAbortedWith());
        assertEquals(401, context.getAbortedWith().getStatus());
    }

    @Test
    @DisplayName("L'accès avec un jeton valide établit le SecurityContext et le TenantContext")
    void authenticatesValidTokenAndActivatesTenantContext() throws IOException {
        AuthResult auth = authService.login("doctor@medscan.org", SeedUserRegistry.DEFAULT_PASSWORD);

        FakeContainerRequestContext context = new FakeContainerRequestContext("/v1/portal/doctor");
        context.setHeader("Authorization", "Bearer " + auth.accessToken());

        filter.filter(context);

        assertNull(context.getAbortedWith(), "La requête authentifiée ne doit pas être interrompue");

        // Verify SecurityContext
        assertNotNull(context.getSecurityContext());
        assertTrue(context.getSecurityContext().getUserPrincipal() instanceof MedscanPrincipal);
        MedscanPrincipal principal = (MedscanPrincipal) context.getSecurityContext().getUserPrincipal();
        assertEquals("doctor@medscan.org", principal.getName());
        assertTrue(context.getSecurityContext().isUserInRole("DOCTOR"));

        // Verify TenantContext
        assertTrue(tenantContext.isActive());
        assertEquals(SeedUserRegistry.TENANT_HOSPITAL, tenantContext.requireTenantId());
        assertEquals(auth.account().userId(), tenantContext.requireUserId());
        assertTrue(tenantContext.hasRole("DOCTOR"));
    }
}
