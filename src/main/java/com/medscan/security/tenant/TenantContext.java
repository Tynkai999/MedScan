package com.medscan.security.tenant;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped holder for verified tenant and user context.
 * The context can only be activated once per request.
 * Any tenant switching during request execution is strictly prohibited.
 */
@RequestScoped
public class TenantContext {

    private UUID tenantId;
    private UUID userId;
    private String username;
    private Set<String> roles = Collections.emptySet();
    private Set<String> permissions = Collections.emptySet();
    private boolean active;

    public void activate(VerifiedTenantIdentity identity) {
        Objects.requireNonNull(identity, "VerifiedTenantIdentity must not be null.");

        if (active) {
            throw new IllegalStateException("The tenant context cannot be changed during a request.");
        }

        this.tenantId = identity.tenantId();
        this.userId = identity.userId();
        this.username = identity.username();
        this.roles = identity.roles();
        this.permissions = identity.permissions();
        this.active = true;
    }

    public UUID requireTenantId() {
        if (!active) {
            throw new TenantContextUnavailableException();
        }
        return tenantId;
    }

    public UUID requireUserId() {
        if (!active) {
            throw new TenantContextUnavailableException();
        }
        return userId;
    }

    public String requireUsername() {
        if (!active) {
            throw new TenantContextUnavailableException();
        }
        return username;
    }

    public Set<String> getRoles() {
        if (!active) {
            throw new TenantContextUnavailableException();
        }
        return roles;
    }

    public Set<String> getPermissions() {
        if (!active) {
            throw new TenantContextUnavailableException();
        }
        return permissions;
    }

    public boolean hasRole(String role) {
        if (!active) {
            throw new TenantContextUnavailableException();
        }
        return roles != null && roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        if (!active) {
            throw new TenantContextUnavailableException();
        }
        return permissions != null && permissions.contains(permission);
    }

    public boolean isActive() {
        return active;
    }
}
