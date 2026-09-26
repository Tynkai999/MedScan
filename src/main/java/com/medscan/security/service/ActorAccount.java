package com.medscan.security.service;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Representation of an actor account in MedScan Enterprise.
 */
public record ActorAccount(
        UUID userId,
        String username,
        String email,
        String password,
        UUID tenantId,
        String tenantCode,
        String displayName,
        Set<String> roles,
        Set<String> permissions) {

    public ActorAccount {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        email = (email == null) ? username : email.trim();
        displayName = (displayName == null) ? username : displayName.trim();
        roles = (roles == null) ? Collections.emptySet() : Collections.unmodifiableSet(roles);
        permissions = (permissions == null) ? Collections.emptySet() : Collections.unmodifiableSet(permissions);
    }
}
