package com.medscan.security.filter;

import java.io.IOException;
import java.util.Objects;

import com.medscan.security.MedscanPrincipal;
import com.medscan.security.MedscanSecurityContext;
import com.medscan.security.PublicEndpoint;
import com.medscan.security.jwt.InvalidTokenException;
import com.medscan.security.jwt.JwtClaims;
import com.medscan.security.jwt.JwtTokenService;
import com.medscan.security.jwt.TokenExpiredException;
import com.medscan.security.tenant.TenantContext;
import com.medscan.security.tenant.VerifiedTenantIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS ContainerRequestFilter validating incoming Bearer JWT tokens.
 * Establishes the MedscanSecurityContext and activates the RequestScoped TenantContext.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class JwtAuthenticationFilter implements ContainerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    private JwtTokenService tokenService;

    @Inject
    private TenantContext tenantContext;

    @Context
    private ResourceInfo resourceInfo;

    public JwtAuthenticationFilter() {
    }

    public JwtAuthenticationFilter(JwtTokenService tokenService, TenantContext tenantContext, ResourceInfo resourceInfo) {
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService must not be null");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext must not be null");
        this.resourceInfo = resourceInfo;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (isPublicEndpoint(requestContext)) {
            return;
        }

        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            abortUnauthorized(requestContext, "En-tête Authorization Bearer manquant ou invalide.");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            JwtClaims claims = tokenService.validateAndParse(token);

            VerifiedTenantIdentity identity = new VerifiedTenantIdentity(
                    claims.tenantId(),
                    claims.subject(),
                    claims.preferredUsername(),
                    claims.roles(),
                    claims.permissions());

            MedscanPrincipal principal = new MedscanPrincipal(identity);
            boolean secure = requestContext.getSecurityContext() != null && requestContext.getSecurityContext().isSecure();
            requestContext.setSecurityContext(new MedscanSecurityContext(principal, secure));

            if (tenantContext != null) {
                tenantContext.activate(identity);
            }
        } catch (TokenExpiredException e) {
            abortUnauthorized(requestContext, "Le jeton d'authentification a expiré.");
        } catch (InvalidTokenException e) {
            abortUnauthorized(requestContext, "Jeton d'authentification invalide ou altéré.");
        }
    }

    private boolean isPublicEndpoint(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo() != null ? requestContext.getUriInfo().getPath() : "";
        if (path.contains("v1/health") || path.contains("v1/auth/login") || path.contains("v1/auth/refresh") || path.contains("openapi")) {
            return true;
        }

        if (resourceInfo != null) {
            if (resourceInfo.getResourceMethod() != null && resourceInfo.getResourceMethod().isAnnotationPresent(PublicEndpoint.class)) {
                return true;
            }
            if (resourceInfo.getResourceClass() != null && resourceInfo.getResourceClass().isAnnotationPresent(PublicEndpoint.class)) {
                return true;
            }
        }

        return false;
    }

    private void abortUnauthorized(ContainerRequestContext requestContext, String detail) {
        String body = String.format(
                "{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Non authentifié\",\"status\":401,\"detail\":\"%s\"}",
                detail);

        requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .header("WWW-Authenticate", "Bearer error=\"invalid_token\"")
                        .entity(body)
                        .build());
    }
}
