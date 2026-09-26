package com.medscan.security;

import java.security.Principal;
import java.util.Objects;

import jakarta.ws.rs.core.SecurityContext;

/**
 * Custom JAX-RS SecurityContext implementation linking to the verified MedScan principal.
 */
public class MedscanSecurityContext implements SecurityContext {

    private final MedscanPrincipal principal;
    private final boolean secure;

    public MedscanSecurityContext(MedscanPrincipal principal, boolean secure) {
        this.principal = Objects.requireNonNull(principal, "MedscanPrincipal must not be null.");
        this.secure = secure;
    }

    @Override
    public Principal getUserPrincipal() {
        return principal;
    }

    @Override
    public boolean isUserInRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        return principal.hasRole(role.trim());
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public String getAuthenticationScheme() {
        return "Bearer";
    }

    public MedscanPrincipal getMedscanPrincipal() {
        return principal;
    }
}
