package com.medscan.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.api.v1.portal.ActorPortalResource;
import com.medscan.security.MedscanPrincipal;
import com.medscan.security.MedscanSecurityContext;
import com.medscan.security.tenant.VerifiedTenantIdentity;

class RoleAuthorizationFilterTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        TestRuntimeDelegate.install();
    }

    @Test
    @DisplayName("Un médecin accède au portail médecin (@RolesAllowed DOCTOR)")
    void allowsDoctorToAccessDoctorPortal() throws NoSuchMethodException, IOException {
        Method doctorMethod = ActorPortalResource.class.getMethod("getDoctorPortal", jakarta.ws.rs.core.SecurityContext.class);
        RoleAuthorizationFilter filter = new RoleAuthorizationFilter(new FakeResourceInfo(doctorMethod, ActorPortalResource.class));

        FakeContainerRequestContext context = new FakeContainerRequestContext("/v1/portal/doctor");
        VerifiedTenantIdentity identity = new VerifiedTenantIdentity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "doctor@medscan.org",
                Set.of("DOCTOR"),
                Set.of());
        context.setSecurityContext(new MedscanSecurityContext(new MedscanPrincipal(identity), true));

        filter.filter(context);

        assertNull(context.getAbortedWith(), "Le médecin doit être autorisé");
    }

    @Test
    @DisplayName("Un patient tentant d'accéder au portail médecin reçoit 403 Forbidden")
    void deniesPatientFromAccessingDoctorPortal() throws NoSuchMethodException, IOException {
        Method doctorMethod = ActorPortalResource.class.getMethod("getDoctorPortal", jakarta.ws.rs.core.SecurityContext.class);
        RoleAuthorizationFilter filter = new RoleAuthorizationFilter(new FakeResourceInfo(doctorMethod, ActorPortalResource.class));

        FakeContainerRequestContext context = new FakeContainerRequestContext("/v1/portal/doctor");
        VerifiedTenantIdentity identity = new VerifiedTenantIdentity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "patient@medscan.org",
                Set.of("PATIENT"),
                Set.of());
        context.setSecurityContext(new MedscanSecurityContext(new MedscanPrincipal(identity), true));

        filter.filter(context);

        assertNotNull(context.getAbortedWith());
        assertEquals(403, context.getAbortedWith().getStatus());
    }

    @Test
    @DisplayName("Un super-admin accède aux portails d'administration multi-rôles")
    void allowsSuperAdminToAccessTenantAdminPortal() throws NoSuchMethodException, IOException {
        Method adminMethod = ActorPortalResource.class.getMethod("getTenantAdminPortal", jakarta.ws.rs.core.SecurityContext.class);
        RoleAuthorizationFilter filter = new RoleAuthorizationFilter(new FakeResourceInfo(adminMethod, ActorPortalResource.class));

        FakeContainerRequestContext context = new FakeContainerRequestContext("/v1/portal/tenant-admin");
        VerifiedTenantIdentity identity = new VerifiedTenantIdentity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "superadmin@medscan.org",
                Set.of("SUPER_ADMIN"),
                Set.of());
        context.setSecurityContext(new MedscanSecurityContext(new MedscanPrincipal(identity), true));

        filter.filter(context);

        assertNull(context.getAbortedWith(), "Le Super Admin doit être autorisé");
    }
}
