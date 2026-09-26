package com.medscan.api.v1.portal;

import com.medscan.security.MedscanPrincipal;
import com.medscan.security.tenant.TenantContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/**
 * REST resource exposing actor-specific portals protected by @RolesAllowed annotations.
 * Proves that each actor can access their authorized space and is denied access to other spaces.
 */
@Path("/v1/portal")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ActorPortalResource {

    @Inject
    private TenantContext tenantContext;

    public ActorPortalResource() {
    }

    public ActorPortalResource(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @GET
    @Path("/patient")
    @RolesAllowed("PATIENT")
    public Response getPatientPortal(@Context SecurityContext securityContext) {
        return buildResponse("Espace Patient", "PATIENT", securityContext,
                "Bienvenue sur votre carnet de santé numérique MedScan.");
    }

    @GET
    @Path("/doctor")
    @RolesAllowed("DOCTOR")
    public Response getDoctorPortal(@Context SecurityContext securityContext) {
        return buildResponse("Portail Médecin", "DOCTOR", securityContext,
                "Accès autorisé au dossier clinique et aux consultations.");
    }

    @GET
    @Path("/radiologist")
    @RolesAllowed("RADIOLOGIST")
    public Response getRadiologistPortal(@Context SecurityContext securityContext) {
        return buildResponse("Portail Radiologie & Imagerie", "RADIOLOGIST", securityContext,
                "Accès autorisé à l'imagerie médicale et aux modules d'analyse IA.");
    }

    @GET
    @Path("/nurse")
    @RolesAllowed("NURSE")
    public Response getNursePortal(@Context SecurityContext securityContext) {
        return buildResponse("Portail Soins Infirmiers", "NURSE", securityContext,
                "Accès autorisé à l'enregistrement des constantes et soins.");
    }

    @GET
    @Path("/lab-technician")
    @RolesAllowed("LAB_TECHNICIAN")
    public Response getLabTechnicianPortal(@Context SecurityContext securityContext) {
        return buildResponse("Portail Laboratoire d'Analyses", "LAB_TECHNICIAN", securityContext,
                "Accès autorisé aux résultats d'examens biologiques.");
    }

    @GET
    @Path("/pharmacist")
    @RolesAllowed("PHARMACIST")
    public Response getPharmacistPortal(@Context SecurityContext securityContext) {
        return buildResponse("Portail Pharmacie d'Officine", "PHARMACIST", securityContext,
                "Accès autorisé au catalogue, stocks et ordonnances.");
    }

    @GET
    @Path("/delivery")
    @RolesAllowed("DELIVERY_AGENT")
    public Response getDeliveryPortal(@Context SecurityContext securityContext) {
        return buildResponse("Portail Livraison & Logistique", "DELIVERY_AGENT", securityContext,
                "Accès autorisé aux missions d'acheminement pharmaceutique.");
    }

    @GET
    @Path("/tenant-admin")
    @RolesAllowed({"TENANT_ADMIN", "SUPER_ADMIN"})
    public Response getTenantAdminPortal(@Context SecurityContext securityContext) {
        return buildResponse("Administration Établissement", "TENANT_ADMIN", securityContext,
                "Accès autorisé à la gestion des départements et du personnel.");
    }

    @GET
    @Path("/super-admin")
    @RolesAllowed("SUPER_ADMIN")
    public Response getSuperAdminPortal(@Context SecurityContext securityContext) {
        return buildResponse("Super-Administration MedScan", "SUPER_ADMIN", securityContext,
                "Accès autorisé à la gouvernance globale de la plateforme.");
    }

    @GET
    @Path("/auditor")
    @RolesAllowed({"AUDITOR", "SUPER_ADMIN"})
    public Response getAuditorPortal(@Context SecurityContext securityContext) {
        return buildResponse("Portail Audit & Conformité DPO", "AUDITOR", securityContext,
                "Accès autorisé aux pistes d'audit et rapports de sécurité.");
    }

    private Response buildResponse(String portalName, String role, SecurityContext securityContext, String message) {
        MedscanPrincipal principal = (MedscanPrincipal) securityContext.getUserPrincipal();
        ActorPortalResponse response = new ActorPortalResponse(
                portalName,
                role,
                principal.getUserId(),
                principal.getName(),
                principal.getTenantId(),
                message);
        return Response.ok(response).build();
    }
}
