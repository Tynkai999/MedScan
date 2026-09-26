package com.medscan.security;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.medscan.security.tenant.VerifiedTenantIdentity;

/**
 * Principal implementation representing an authenticated MedScan user.
 */
public class MedscanPrincipal implements Principal {

    private final VerifiedTenantIdentity identity;

    public MedscanPrincipal(VerifiedTenantIdentity identity) {
        this.identity = Objects.requireNonNull(identity, "VerifiedTenantIdentity must not be null.");
    }

    @Override
    public String getName() {
        return identity.username();
    }

    public UUID getUserId() {
        return identity.userId();
    }

    public UUID getTenantId() {
        return identity.tenantId();
    }

    public Set<String> getRoles() {
        return identity.roles();
    }

    public Set<String> getPermissions() {
        return identity.permissions();
    }

    public boolean hasRole(String role) {
        return identity.hasRole(role);
    }

    public VerifiedTenantIdentity getIdentity() {
        return identity;
    }

    @Override
    public String toString() {
        return "MedscanPrincipal{" +
                "userId=" + identity.userId() +
                ", username='" + identity.username() + '\'' +
                ", tenantId=" + identity.tenantId() +
                ", roles=" + identity.roles() +
                '}';
    }
}
