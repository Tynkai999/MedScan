package com.medscan.api.v1.portal;

import java.io.Serializable;
import java.util.UUID;

/**
 * Confirmation payload returned when an actor accesses their authorized portal.
 */
public record ActorPortalResponse(
        String portalName,
        String authorizedRole,
        UUID userId,
        String username,
        UUID tenantId,
        String message) implements Serializable {
}
