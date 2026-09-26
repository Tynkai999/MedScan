package com.medscan.api.v1.auth;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/**
 * Public profile of the authenticated actor.
 * Conforms to data minimization: contains identity, tenancy and security roles without clinical data.
 */
public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        UUID tenantId,
        String tenantCode,
        Set<String> roles,
        Set<String> permissions) implements Serializable {
}
