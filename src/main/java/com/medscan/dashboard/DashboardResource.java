package com.medscan.dashboard;

import com.medscan.security.MedscanPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Ressource REST exposant les métriques et statistiques de tableau de bord pour tous les acteurs (MOD-02 / MOD-03).
 */
@Path("/v1/dashboard")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @Inject
    private DashboardService dashboardService;

    public DashboardResource() {
    }

    public DashboardResource(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GET
    @Path("/stats")
    @RolesAllowed({
            "DOCTOR", "PHARMACIST", "RADIOLOGIST", "NURSE", "DELIVERY_AGENT",
            "TENANT_ADMIN", "SUPER_ADMIN", "PATIENT", "AUDITOR", "LAB_TECHNICIAN"
    })
    public Response getDashboardStats(@Context SecurityContext securityContext, @QueryParam("role") String requestedRole) {
        MedscanPrincipal principal = (MedscanPrincipal) securityContext.getUserPrincipal();
        String primaryRole = principal.getRoles().isEmpty() ? "DOCTOR" : principal.getRoles().iterator().next();
        String roleToUse = (requestedRole != null && !requestedRole.isBlank()) ? requestedRole : primaryRole;

        ActorDashboardStats stats = dashboardService.getDashboardStats(
                principal.getUserId(),
                principal.getName(),
                roleToUse,
                principal.getTenantId()
        );
        return Response.ok(DashboardJsonMapper.toJson(stats)).build();
    }
}
