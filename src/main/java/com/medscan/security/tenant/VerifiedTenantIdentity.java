package com.medscan.security.tenant;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable value object representing a server-verified tenant identity.
 * Carries verified user identity, tenant affiliation, assigned roles, and fine-grained permissions.
 */
public record VerifiedTenantIdentity(
        UUID tenantId,
        UUID userId,
        String username,
        Set<String> roles,
        Set<String> permissions) {

    public VerifiedTenantIdentity {
        Objects.requireNonNull(tenantId, "A verified tenant identity requires a non-null tenant identifier.");
        Objects.requireNonNull(userId, "A verified tenant identity requires a non-null user identifier.");

        username = (username == null || username.isBlank()) ? userId.toString() : username.trim();
        roles = (roles == null) ? Collections.emptySet() : Collections.unmodifiableSet(roles);
        permissions = (permissions == null) ? Collections.emptySet() : Collections.unmodifiableSet(permissions);
    }

    /**
     * Backward-compatible constructor for two-argument invocation.
     */
    public VerifiedTenantIdentity(UUID tenantId, UUID userId) {
        this(tenantId, userId, userId.toString(), Collections.emptySet(), Collections.emptySet());
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
