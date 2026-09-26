package com.medscan.api.v1.auth;

import java.util.Objects;

import com.medscan.security.MedscanPrincipal;
import com.medscan.security.PublicEndpoint;
import com.medscan.security.service.ActorAccount;
import com.medscan.security.service.AuthResult;
import com.medscan.security.service.AuthService;
import com.medscan.security.service.AuthenticationFailedException;
import com.medscan.security.service.SeedUserRegistry;
import com.medscan.security.tenant.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Authentication and identity verification REST endpoint.
 * Provides login, token refresh, and identity inspection (/me) for all actors.
 */
@Path("/v1/auth")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    private AuthService authService;

    @Inject
    private SeedUserRegistry userRegistry;

    @Inject
    private TenantContext tenantContext;

    public AuthResource() {
    }

    public AuthResource(AuthService authService, SeedUserRegistry userRegistry, TenantContext tenantContext) {
        this.authService = Objects.requireNonNull(authService, "authService is required");
        this.userRegistry = Objects.requireNonNull(userRegistry, "userRegistry is required");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext is required");
    }

    @POST
    @Path("/login")
    @PublicEndpoint
    public Response login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"Nom d'utilisateur et mot de passe requis.\"}")
                    .build();
        }

        try {
            AuthResult result = authService.login(request.username(), request.password());
            ActorAccount account = result.account();

            UserProfileResponse profile = new UserProfileResponse(
                    account.userId(),
                    account.username(),
                    account.email(),
                    account.displayName(),
                    account.tenantId(),
                    account.tenantCode(),
                    account.roles(),
                    account.permissions());

            LoginResponse response = new LoginResponse(
                    result.accessToken(),
                    result.refreshToken(),
                    result.tokenType(),
                    result.expiresInSeconds(),
                    profile);

            return Response.ok(response).build();
        } catch (AuthenticationFailedException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(String.format(
                            "{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Échec d'authentification\",\"status\":401,\"detail\":\"%s\"}",
                            e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/me")
    public Response getCurrentUser(@Context SecurityContext securityContext) {
        if (securityContext == null || !(securityContext.getUserPrincipal() instanceof MedscanPrincipal principal)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Non authentifié\",\"status\":401,\"detail\":\"Session non authentifiée.\"}")
                    .build();
        }

        ActorAccount account = userRegistry.findById(principal.getUserId())
                .orElse(new ActorAccount(
                        principal.getUserId(),
                        principal.getName(),
                        principal.getName(),
                        "***",
                        principal.getTenantId(),
                        "TENANT",
                        principal.getName(),
                        principal.getRoles(),
                        principal.getPermissions()));

        UserProfileResponse profile = new UserProfileResponse(
                account.userId(),
                account.username(),
                account.email(),
                account.displayName(),
                account.tenantId(),
                account.tenantCode(),
                account.roles(),
                account.permissions());

        return Response.ok(profile).build();
    }

    @POST
    @Path("/refresh")
    @PublicEndpoint
    public Response refresh(RefreshTokenRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"Le jeton de rafraîchissement est requis.\"}")
                    .build();
        }

        try {
            AuthResult result = authService.refreshToken(request.refreshToken());
            ActorAccount account = result.account();

            UserProfileResponse profile = new UserProfileResponse(
                    account.userId(),
                    account.username(),
                    account.email(),
                    account.displayName(),
                    account.tenantId(),
                    account.tenantCode(),
                    account.roles(),
                    account.permissions());

            LoginResponse response = new LoginResponse(
                    result.accessToken(),
                    result.refreshToken(),
                    result.tokenType(),
                    result.expiresInSeconds(),
                    profile);

            return Response.ok(response).build();
        } catch (RuntimeException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(String.format(
                            "{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Jeton invalide\",\"status\":401,\"detail\":\"%s\"}",
                            e.getMessage()))
                    .build();
        }
    }
}
