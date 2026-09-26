package com.medscan.security.filter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;

import jakarta.annotation.Priority;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS ContainerRequestFilter enforcing role-based access control (RBAC).
 * Supports standard Jakarta annotations: @RolesAllowed, @PermitAll, @DenyAll.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
@ApplicationScoped
public class RoleAuthorizationFilter implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    public RoleAuthorizationFilter() {
    }

    public RoleAuthorizationFilter(ResourceInfo resourceInfo) {
        this.resourceInfo = resourceInfo;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (resourceInfo == null) {
            return;
        }

        Method method = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();

        if (method == null || resourceClass == null) {
            return;
        }

        // 1. Check @DenyAll
        if (method.isAnnotationPresent(DenyAll.class) || resourceClass.isAnnotationPresent(DenyAll.class)) {
            abortForbidden(requestContext, "L'accès à cette ressource est formellement interdit.");
            return;
        }

        // 2. Check @RolesAllowed on method first, then class
        RolesAllowed rolesAllowed = method.getAnnotation(RolesAllowed.class);
        if (rolesAllowed == null) {
            rolesAllowed = resourceClass.getAnnotation(RolesAllowed.class);
        }

        if (rolesAllowed != null) {
            SecurityContext securityContext = requestContext.getSecurityContext();
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                abortUnauthorized(requestContext, "Authentification requise pour cette ressource.");
                return;
            }

            String[] allowedRoles = rolesAllowed.value();
            boolean authorized = Arrays.stream(allowedRoles)
                    .anyMatch(securityContext::isUserInRole);

            if (!authorized) {
                abortForbidden(requestContext, "Vos habilitations ne vous permettent pas d'accéder à cette ressource.");
                return;
            }
        }
    }

    private void abortUnauthorized(ContainerRequestContext requestContext, String detail) {
        String body = String.format(
                "{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Non authentifié\",\"status\":401,\"detail\":\"%s\"}",
                detail);

        requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(body)
                        .build());
    }

    private void abortForbidden(ContainerRequestContext requestContext, String detail) {
        String body = String.format(
                "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"%s\"}",
                detail);

        requestContext.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(body)
                        .build());
    }
}
