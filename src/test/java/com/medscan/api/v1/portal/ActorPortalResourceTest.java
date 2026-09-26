package com.medscan.api.v1.portal;

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
import com.medscan.security.service.SeedUserRegistry;
import com.medscan.security.tenant.TenantContext;
import com.medscan.security.tenant.VerifiedTenantIdentity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

class ActorPortalResourceTest {

    private ActorPortalResource portalResource;

    @BeforeEach
    void setUp() {
        portalResource = new ActorPortalResource(new TenantContext());
    }

    private SecurityContext createSecurityContext(String username, String role, UUID tenantId) {
        VerifiedTenantIdentity identity = new VerifiedTenantIdentity(
                tenantId,
                UUID.randomUUID(),
                username,
                Set.of(role),
                Set.of());
        return new MedscanSecurityContext(new MedscanPrincipal(identity), true);
    }

    @Test
    @DisplayName("Portail Patient accessible avec rôle PATIENT")
    void testPatientPortal() {
        SecurityContext ctx = createSecurityContext("patient@medscan.org", "PATIENT", SeedUserRegistry.TENANT_HOSPITAL);
        Response response = portalResource.getPatientPortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("PATIENT", entity.authorizedRole());
    }

    @Test
    @DisplayName("Portail Médecin accessible avec rôle DOCTOR")
    void testDoctorPortal() {
        SecurityContext ctx = createSecurityContext("doctor@medscan.org", "DOCTOR", SeedUserRegistry.TENANT_HOSPITAL);
        Response response = portalResource.getDoctorPortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("DOCTOR", entity.authorizedRole());
    }

    @Test
    @DisplayName("Portail Radiologue accessible avec rôle RADIOLOGIST")
    void testRadiologistPortal() {
        SecurityContext ctx = createSecurityContext("radiologist@medscan.org", "RADIOLOGIST", SeedUserRegistry.TENANT_HOSPITAL);
        Response response = portalResource.getRadiologistPortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("RADIOLOGIST", entity.authorizedRole());
    }

    @Test
    @DisplayName("Portail Infirmier accessible avec rôle NURSE")
    void testNursePortal() {
        SecurityContext ctx = createSecurityContext("nurse@medscan.org", "NURSE", SeedUserRegistry.TENANT_HOSPITAL);
        Response response = portalResource.getNursePortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("NURSE", entity.authorizedRole());
    }

    @Test
    @DisplayName("Portail Laboratoire accessible avec rôle LAB_TECHNICIAN")
    void testLabTechnicianPortal() {
        SecurityContext ctx = createSecurityContext("labtech@medscan.org", "LAB_TECHNICIAN", SeedUserRegistry.TENANT_LAB);
        Response response = portalResource.getLabTechnicianPortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("LAB_TECHNICIAN", entity.authorizedRole());
    }

    @Test
    @DisplayName("Portail Pharmacie accessible avec rôle PHARMACIST")
    void testPharmacistPortal() {
        SecurityContext ctx = createSecurityContext("pharmacist@medscan.org", "PHARMACIST", SeedUserRegistry.TENANT_PHARMACY);
        Response response = portalResource.getPharmacistPortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("PHARMACIST", entity.authorizedRole());
    }

    @Test
    @DisplayName("Portail Livraison accessible avec rôle DELIVERY_AGENT")
    void testDeliveryPortal() {
        SecurityContext ctx = createSecurityContext("delivery@medscan.org", "DELIVERY_AGENT", SeedUserRegistry.TENANT_DELIVERY);
        Response response = portalResource.getDeliveryPortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("DELIVERY_AGENT", entity.authorizedRole());
    }

    @Test
    @DisplayName("Portail Administration Établissement accessible avec rôle TENANT_ADMIN")
    void testTenantAdminPortal() {
        SecurityContext ctx = createSecurityContext("tenantadmin@medscan.org", "TENANT_ADMIN", SeedUserRegistry.TENANT_HOSPITAL);
        Response response = portalResource.getTenantAdminPortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("TENANT_ADMIN", entity.authorizedRole());
    }

    @Test
    @DisplayName("Portail Super-Admin accessible avec rôle SUPER_ADMIN")
    void testSuperAdminPortal() {
        SecurityContext ctx = createSecurityContext("superadmin@medscan.org", "SUPER_ADMIN", SeedUserRegistry.TENANT_PLATFORM);
        Response response = portalResource.getSuperAdminPortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("SUPER_ADMIN", entity.authorizedRole());
    }

    @Test
    @DisplayName("Portail Audit & DPO accessible avec rôle AUDITOR")
    void testAuditorPortal() {
        SecurityContext ctx = createSecurityContext("auditor@medscan.org", "AUDITOR", SeedUserRegistry.TENANT_PLATFORM);
        Response response = portalResource.getAuditorPortal(ctx);
        assertEquals(200, response.getStatus());
        ActorPortalResponse entity = (ActorPortalResponse) response.getEntity();
        assertEquals("AUDITOR", entity.authorizedRole());
    }
}
